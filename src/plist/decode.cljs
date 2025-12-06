(ns plist.decode
  "Binary plist decoder.

  Decodes Apple's binary property list format into Clojure data structures.
  Supports: nil, booleans, integers, strings, and maps (dictionaries).")

(defn- bstring->dataview
  "Convert a binary string to a DataView for byte-level operations"
  [bstring]
  (let [length (count bstring)
        buffer (js/ArrayBuffer. length)
        view (js/DataView. buffer)]
    (dotimes [i length]
      (.setUint8 view i (.charCodeAt bstring i)))
    view))

(defn- get-byte [view idx]
  (.getUint8 view idx))

(defn- get-int
  "Read an integer from the view at the given index with the specified size"
  [view idx size]
  (let [bytes (js/DataView. (.slice (.-buffer view) idx (+ idx size)))]
    (reduce
     (fn [a i]
       (+ (bit-shift-left a 8) (.getUint8 bytes i)))
     0
     (range size))))

(defn- get-bigint [view idx]
  (js/Number (.getBigUint64 view idx)))

(declare read-object)

(defn- read-primitive [obj-info]
  (case obj-info
    0 nil
    8 false
    9 true
    (throw (js/Error. (str "Unknown primitive type: " obj-info)))))

(defn- read-int [ctx view offset]
  ;; Binary plist format uses mixed signed/unsigned:
  ;; - Markers 0x10 (1-byte), 0x11 (2-byte), 0x12 (4-byte): UNSIGNED
  ;; - Markers 0x13 (8-byte), 0x14 (16-byte): SIGNED
  ;; This matches Python's plistlib: signed=tokenL >= 3
  (let [new-offset (+ (:offset ctx) offset)
        obj-info (bit-and 0X0F (get-byte view new-offset))
        length (bit-shift-left 1 obj-info)
        signed? (>= obj-info 3)]  ; obj-info 3 = 8 bytes, 4 = 16 bytes
     (case length
       1 (.getUint8 view (+ new-offset 1))    ; unsigned 1-byte
       2 (.getUint16 view (+ new-offset 1))   ; unsigned 2-byte
       4 (.getUint32 view (+ new-offset 1))   ; unsigned 4-byte
       8 (if signed?
           (let [big-int (.getBigInt64 view (+ new-offset 1))]
             (js/Number big-int))   ; signed 8-byte - convert BigInt to Number
           (let [big-uint (.getBigUint64 view (+ new-offset 1))]
             (js/Number big-uint))) ; unsigned 8-byte (rare)
       16 (let [high (.getBigInt64 view (+ new-offset 1))
                low (.getBigInt64 view (+ new-offset 1 8))]
            (js/Number (+ (bit-shift-left high (js/BigInt 64)) low))))))

(defn- read-real [_ctx _view _offset]
  (throw (js/Error. "Real number decoding not implemented")))

(defn- read-date [_ctx _view _offset]
  (throw (js/Error. "Date decoding not implemented")))

(defn- read-data [_ctx _view _offset]
  (throw (js/Error. "Data decoding not implemented")))

(defn- get-obj-range
  "Determine the offset and length of a variable-length object"
  [ctx view offset]
  (let [obj-info (bit-and 0X0F (get-byte view (+ (:offset ctx) offset)))]
    (if (not= 0xF obj-info)
        [(inc offset) obj-info]

        (let [offset (inc offset)]
          [(+ offset 1 (bit-shift-left 1 (bit-and 0x0F (get-byte view (+ (:offset ctx) offset)))))
           (read-int ctx view offset)]))))

(defn- read-ascii [ctx view offset]
  (let [[string-offset length] (get-obj-range ctx view offset)

        start (+ (:offset ctx) string-offset)
        end (+ start length)
        string-buffer (.slice (.-buffer view) start end)
        decoder (js/TextDecoder. "ascii")]

     (.decode decoder string-buffer)))

(defn- read-unicode [_ctx _view _offset]
  (throw (js/Error. "Unicode string decoding not implemented")))

(defn- read-UID [_ctx _view _offset]
  (throw (js/Error. "UID decoding not implemented")))

(defn- read-array [_ctx _view _offset]
  (throw (js/Error. "Array decoding not implemented")))

(defn- read-set [_ctx _view _offset]
  (throw (js/Error. "Set decoding not implemented")))

(defn- read-dict [ctx view offset]
  (let [[dict-offset dict-length] (get-obj-range ctx view offset)
        keys #js []
        values #js []]

    (dotimes [i dict-length]
      (let [obj-ref (get-int view (+ (:offset ctx) dict-offset (* i (:object-ref-size ctx))) (:object-ref-size ctx))]
        (.push keys (read-object ctx view obj-ref))))

    (dotimes [i dict-length]
      (let [obj-ref (get-int view (+ (:offset ctx) dict-offset (* (+ i dict-length) (:object-ref-size ctx))) (:object-ref-size ctx))]
        (.push values (read-object ctx view obj-ref))))

    (reduce (fn [d i] (assoc d (aget keys i) (aget values i))) {} (range dict-length))))

(defn- read-object
  "Read an object from the plist at the given object reference"
  [{:keys [offset-table offset-int-size offset] :as context} view obj-ref]
  (let [new-offset (get-int offset-table (* obj-ref offset-int-size) offset-int-size)
        marker (get-byte view (+ offset new-offset))
        obj-type (bit-shift-right (bit-and marker 0xF0) 4)
        obj-info (bit-and marker 0x0F)]

    (case obj-type
      0x00 (read-primitive obj-info)
      0x01 (read-int context view new-offset)
      0x02 (read-real context view new-offset)
      0x03 (read-date context view new-offset)
      0x04 (read-data context view new-offset)
      0x05 (read-ascii context view new-offset)
      0x06 (read-unicode context view new-offset)
      0x08 (read-UID context view new-offset)
      0x0A (read-array context view new-offset)
      0x0C (read-set context view new-offset)
      0x0D (read-dict context view new-offset)
      (throw (js/Error. (str "Unknown object type: 0x" (.toString obj-type 16)))))))

(defn parse
  "Parse a binary plist string and return the decoded data structure"
  [bplist]
  (let [view (bstring->dataview bplist)
        length (.-byteLength view)
        trailer-offset (- length 32)
        _sort-version        (get-byte view (+ trailer-offset 5))
        offset-int-size     (get-byte view (+ trailer-offset 6))
        object-ref-size     (get-byte view (+ trailer-offset 7))
        _object-count        (get-bigint view (+ trailer-offset 8))
        root-object         (get-bigint view (+ trailer-offset 16))
        offset-table-offset (get-bigint view (+ trailer-offset 24))
        offset-table (js/DataView. (.slice (.-buffer view) offset-table-offset
                                          (+ offset-table-offset
                                             (* _object-count offset-int-size))))
        context
        {:offset 0
         :length length
         :trailer-offset trailer-offset
         :offset-int-size offset-int-size
         :object-ref-size object-ref-size
         :root-object root-object
         :offset-table-offset offset-table-offset
         :offset-table offset-table}]
    (read-object context view root-object)))
