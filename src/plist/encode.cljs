(ns plist.encode
  "Binary plist encoder.

  Encodes Clojure data structures into Apple's binary property list format.
  Supports: nil, booleans, integers, strings, and maps (dictionaries).")

;; Object type codes (from Apple's binary plist spec)
(def ^:private code-primitive 0)
(def ^:private code-int       1)
;(def ^:private code-real      2)
;(def ^:private code-date      3)
;(def ^:private code-data      4)
(def ^:private code-ascii     5)
;(def ^:private code-unicode   6)
;(def ^:private code-uuid      8)
;(def ^:private code-array    10)
;(def ^:private code-set      12)
(def ^:private code-dict     13)

(defn- bytes-for [_number]
  1)

(defn- ->bstring
  "Convert a collection of byte values to a JavaScript string"
  [coll]
  (apply str (mapv char coll)))

(defn- bits
  "Calculate the number of bits needed to represent a value"
  ([val]
   (bits val 0))
  ([val clamp]
   (let [val-bits (-> val inc js/Math.abs js/Math.log2 js/Math.ceil)]
     (max clamp (bit-shift-left 1 (js/Math.ceil (js/Math.log2 val-bits)))))))

(defn- bytes-vector
  "Convert an integer value to a vector of bytes"
  [val bit-count]
  (assert (= 0 (mod bit-count 8)) (str bit-count " bit values are not supported"))
  (loop [v val
         index 0
         bytes-array []]
    (if (= (* 8 index) bit-count)
      (apply vector (reverse bytes-array))

      (let [next-index (inc index)
            byte (bit-and v 0xFF)]
        (recur (quot (- v byte) 256) next-index (conj bytes-array byte))))))

(defn- get-type-of [value]
  (cond
    (nil?     value) :nil
    (boolean? value) :boolean
    (number?  value) :number
    (string?  value) :string
    (inst?    value) :date
    (map?     value) :map
    (vector?  value) :array
    (set?     value) :set
    :else            :unknown))

(defn- get-value-set
  "Extract a Set of all values from `plist`.
  This is used in the construction of the value offset table,
  in order to only encode values in the data once."
  [plist]
  (let [get-primitive
        (fn [v {^js values :values}]
         (.add values v))
        get-seq
        (fn [v {:keys [^js objects ^js queue]}]
          (.add objects v)
          (doseq [i v] (.push queue i)))
        get-map
        (fn [v {:keys [^js objects ^js queue]}]
         (.add objects v)
         (doseq [[k x] v]
           (.push queue k)
           (.push queue x)))
        ctx
        {:values ^js (js/Set.)
         :objects ^js (js/Set.)
         :queue #js [plist]}
        get-ctx-vals (fn [k] (es6-iterator-seq (.values (get ctx k))))]

    (while (> (.-length (:queue ctx)) 0)
      (let [v (.shift (:queue ctx))
            t (get-type-of v)
            get-fn!
            (case t
              (:nil :number :date :string :boolean) get-primitive
              :array get-seq
              :map get-map
              :set get-seq
              (throw (js/Error. (str "Unsupported value type: " t))))]
        (get-fn! v ctx)))

    (concat (get-ctx-vals :values)
            (reverse (get-ctx-vals :objects)))))

(defn- encode-boolean [val]
  (let [left (bit-shift-left code-primitive 4)
        marker (case val
                 nil   (bit-or left 0)
                 false (bit-or left 8)
                 true  (bit-or left 9))]
    [marker]))

(defn- encode-real [_val]
  (throw (js/Error. "Real values in plist are not supported yet")))

(defn- encode-int [val]
  ;; Binary plist format:
  ;; - Negative integers: always encoded as 8-byte signed (marker 0x13)
  ;; - Positive integers: variable length unsigned (markers 0x10, 0x11, 0x12)
  ;;   or 8-byte signed for large values (marker 0x13)
  (let [size (cond
               (neg? val) 64  ; Negative: always 8 bytes (signed)
               (<= val 0xFF) 8     ; 1 byte (unsigned)
               (<= val 0xFFFF) 16  ; 2 bytes (unsigned)
               (<= val 0xFFFFFFFF) 32  ; 4 bytes (unsigned)
               (<= (abs val) (.-MAX_SAFE_INTEGER js/Number)) 64  ; 8 bytes (signed)
               :else 128)  ; 16 bytes (signed)

        obj-info (bit-or (bit-shift-left code-int 4) (js/Math.log2 (quot size 8)))
        obj-val (bytes-vector val size)]

     (-> []
         (conj obj-info)
         (into obj-val))))

(defn- encode-number [val]
  (if (int? val)
    (encode-int val)
    (encode-real val)))

(defn- encode-date [_val]
  (throw (js/Error. "Date values in plist are not supported yet")))

(defn- encode-string [val]
  (let [length (count val)
        obj-info (bit-or (bit-shift-left code-ascii 4) (if (> length 14) 15 length))]

    (cond-> [obj-info]
        (> length 14) (into (encode-int length))
        true (into (map #(.charCodeAt % 0)) (seq val)))))

(defn encode-map-els
  "Encode map elements as references to the value table"
  [coll values _ref-size]
  (for [x coll]
    (let [key-ref (.indexOf values x)]
      (assert (not= -1 key-ref) (str "Attempt to encode object with unknown key: " x))
      key-ref)))

(defn- encode-map [m values ref-size]
  (let [length (count (keys m))
        obj-info (bit-or (bit-shift-left code-dict 4) (if (> length 14) 15 length))]
    (cond-> [obj-info]
      (> length 14) (into (encode-int length))
      true (into (encode-map-els (keys m) values ref-size))
      true (into (encode-map-els (vals m) values ref-size)))))

(defn- +value [values index state]
  (let [val (nth values index)
        type (get-type-of val)]
    (case type
      (:nil :boolean) (encode-boolean val)
      :number (encode-number val)
      :date (encode-date val)
      :string (encode-string val)
      :map (encode-map val values (:ref-size @state)))))

(defn- +values
  ([buffer values state]
   (+values buffer values state 0))
  ([buffer values state index]
   (if (< index (count values))
     (let [new-buffer (into buffer (+value values index state))]
       (swap! state update :offsets conj (count buffer))
       (recur new-buffer values state (inc index)))
     buffer)))

(defn- +header [buffer s]
  (into buffer (map #(.charCodeAt s %)) (range (count s))))

(defn- +offset-table [buffer state]
  (let [table-offset (count buffer)
        offset-size (bits table-offset 8)]
    (swap! state assoc :offset-size (quot offset-size 8) :table-offset table-offset)
    (into buffer (mapcat #(bytes-vector % offset-size)) (:offsets @state))))

(defn- +trailer
  "Write trailer (32 bytes)
   Bytes 0-5: 0-4 = unused, 5 = sort_version (zero)
   Byte 6: size of offset ints in offset table
   Byte 7: size of object refs in arrays and dicts
   Bytes 8-15: Number of offsets in offset table (also is number of objects)
   Bytes 16-23: Element # in offset table which is top level object (aka root-node)
   Bytes 24-31: Offset-table offset"
  [buffer state]
  (-> buffer
     (into [0 0 0 0 0 0])
     (conj (:offset-size @state))
     (conj (:ref-size @state))
     (into (bytes-vector (:object-count @state) 64))
     (into (bytes-vector (dec (:object-count @state)) 64))
     (into (bytes-vector (:table-offset @state) 64))))

(defn write
  "Encode a Clojure data structure as a binary plist string"
  [plist]
  (let [values (get-value-set plist)
        object-count (count values)
        ref-size (bytes-for object-count)
        state (atom {:offsets [] :object-count object-count :ref-size ref-size})]

    (-> []
        (+header "bplist00")
        (+values values state)
        (+offset-table state)
        (+trailer state)
        ->bstring)))
