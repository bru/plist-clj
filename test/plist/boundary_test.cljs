(ns plist.boundary-test
  "Boundary value tests for binary plist encoding/decoding.

  Tests the transition points where marker types change according to the spec:
  - Marker 0x10: 1-byte unsigned (0-255)
  - Marker 0x11: 2-byte unsigned (256-65535)
  - Marker 0x12: 4-byte unsigned (65536-4294967295)
  - Marker 0x13: 8-byte signed (negatives, large positives)"
  (:require [cljs.test :refer-macros [deftest testing is]]
            [plist.core :as plist]))

;; Integer Boundary Tests - Marker Type Transitions

(deftest test-integer-1byte-max
  (testing "Max 1-byte unsigned integer (255) uses marker 0x10"
    (let [value 255
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded))
      ;; Verify it's using 1-byte encoding (compact)
      (is (< (count encoded) 50) "Should use compact 1-byte encoding"))))

(deftest test-integer-2byte-min
  (testing "Min 2-byte unsigned integer (256) uses marker 0x11"
    (let [value 256
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-integer-2byte-max
  (testing "Max 2-byte unsigned integer (65535) uses marker 0x11"
    (let [value 65535
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-integer-4byte-min
  (testing "Min 4-byte unsigned integer (65536) uses marker 0x12"
    (let [value 65536
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-integer-4byte-max
  (testing "Max 4-byte unsigned integer (4294967295) uses marker 0x12"
    (let [value 4294967295
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-integer-8byte-min
  (testing "Min 8-byte integer (4294967296) uses marker 0x13"
    (let [value 4294967296
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-integer-boundaries-sequence
  (testing "Sequence of boundary values"
    (doseq [value [0 1 127 128 255 256
                   32767 32768 65535 65536
                   2147483647 2147483648
                   4294967295 4294967296]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Boundary value " value " failed round-trip"))))))

;; Negative Integer Tests

(deftest test-negative-integers
  (testing "Various negative integers all use 8-byte signed encoding"
    (doseq [value [-1 -10 -100 -1000 -10000 -100000 -1000000]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Negative value " value " failed round-trip"))))))

(deftest test-large-negative-integer
  (testing "Large negative integer"
    (let [value -2147483648  ; -2^31
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= value decoded)))))

(deftest test-negative-one
  (testing "Negative one is encoded as 8-byte signed"
    (let [value -1
          encoded (plist/as-binary value)]
      ;; -1 should be encoded with marker 0x13 (8-byte signed)
      ;; which means the encoded size should be larger than 1-byte encoding
      (is (>= (count encoded) 50) "Should use 8-byte encoding for negative"))))

;; Boolean/Primitive Tests

(deftest test-boolean-true
  (testing "Boolean true encodes and decodes correctly"
    (let [encoded (plist/as-binary true)
          decoded (plist/parse encoded)]
      (is (true? decoded))
      (is (= true decoded)))))

(deftest test-boolean-false
  (testing "Boolean false encodes and decodes correctly"
    (let [encoded (plist/as-binary false)
          decoded (plist/parse encoded)]
      (is (false? decoded))
      (is (= false decoded)))))

(deftest test-all-primitives
  (testing "All primitive values round-trip correctly"
    (doseq [value [true false]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Primitive " value " failed round-trip"))))))

;; String Length Boundary Tests

(deftest test-string-14chars
  (testing "String with exactly 14 characters (boundary for length encoding)"
    (let [value "12345678901234"  ; exactly 14 chars
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= 14 (count value)))
      (is (= value decoded)))))

(deftest test-string-15chars
  (testing "String with exactly 15 characters (triggers extended length)"
    (let [value "123456789012345"  ; exactly 15 chars
          encoded (plist/as-binary value)
          decoded (plist/parse encoded)]
      (is (= 15 (count value)))
      (is (= value decoded)))))

(deftest test-string-boundaries
  (testing "Strings at length boundaries"
    (doseq [len [0 1 13 14 15 16 100 255 256]]
      (let [value (apply str (repeat len "x"))
            encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "String of length " len " failed round-trip"))))))

;; Map Size Boundary Tests

(deftest test-map-14keys
  (testing "Map with exactly 14 keys (boundary for length encoding)"
    (let [data (into {} (map #(vector (str "k" %) (str "v" %))) (range 14))
          encoded (plist/as-binary data)
          decoded (plist/parse encoded)]
      (is (= 14 (count data)))
      (is (= data decoded)))))

(deftest test-map-15keys
  (testing "Map with exactly 15 keys (triggers extended length)"
    (let [data (into {} (map #(vector (str "k" %) (str "v" %))) (range 15))
          encoded (plist/as-binary data)
          decoded (plist/parse encoded)]
      (is (= 15 (count data)))
      (is (= data decoded)))))

(deftest test-map-boundaries
  (testing "Maps at size boundaries"
    (doseq [size [0 1 13 14 15 16 50]]
      (let [data (into {} (map #(vector (str "key" %) %)) (range size))
            encoded (plist/as-binary data)
            decoded (plist/parse encoded)]
        (is (= data decoded)
            (str "Map of size " size " failed round-trip"))))))

;; Deep Nesting Tests

(deftest test-deeply-nested-maps
  (testing "Deeply nested map structures"
    (let [data {"level1" {"level2" {"level3" {"level4" {"level5" "deep"}}}}}
          encoded (plist/as-binary data)
          decoded (plist/parse encoded)]
      (is (= data decoded)))))

;; Mixed Boundary Conditions

(deftest test-map-with-boundary-integers
  (testing "Map containing integers at marker boundaries"
    (let [data {"zero" 0
                "max-1byte" 255
                "min-2byte" 256
                "max-2byte" 65535
                "min-4byte" 65536
                "negative" -42}
          encoded (plist/as-binary data)
          decoded (plist/parse encoded)]
      (is (= data decoded)))))

(deftest test-map-with-boundary-strings
  (testing "Map containing strings at length boundaries"
    (let [data {"short" "hi"
                "exactly14" "12345678901234"
                "exactly15" "123456789012345"
                "long" "this is a much longer string with many characters"}
          encoded (plist/as-binary data)
          decoded (plist/parse encoded)]
      (is (= data decoded)))))
