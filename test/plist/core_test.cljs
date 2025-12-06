(ns plist.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [plist.core :as plist]))

;; Tests for as-binary and parse (round-trip testing)

(deftest test-as-binary-nil
  (testing "as-binary encodes nil as primitive"
    (let [result (plist/as-binary nil)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (nil? (plist/parse result))))))

(deftest test-as-binary-small-integer
  (testing "as-binary encodes small positive integer"
    (let [result (plist/as-binary 42)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= 42 (plist/parse result))))))

(deftest test-as-binary-zero
  (testing "as-binary encodes zero"
    (let [result (plist/as-binary 0)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= 0 (plist/parse result))))))

(deftest test-as-binary-large-integer
  (testing "as-binary encodes large integer"
    (let [result (plist/as-binary 1234567890)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= 1234567890 (plist/parse result))))))

(deftest test-as-binary-simple-string
  (testing "as-binary encodes simple ASCII string"
    (let [result (plist/as-binary "hello")]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= "hello" (plist/parse result))))))

(deftest test-as-binary-empty-string
  (testing "as-binary encodes empty string"
    (let [result (plist/as-binary "")]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= "" (plist/parse result))))))

(deftest test-as-binary-string-with-spaces
  (testing "as-binary encodes string with spaces"
    (let [result (plist/as-binary "hello world")]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= "hello world" (plist/parse result))))))

(deftest test-as-binary-long-string
  (testing "as-binary encodes long string (> 14 chars)"
    (let [long-str "this is a very long string with more than fourteen characters"
          result (plist/as-binary long-str)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= long-str (plist/parse result))))))

(deftest test-as-binary-simple-map
  (testing "as-binary encodes simple map"
    (let [result (plist/as-binary {"key" "value"})]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= {"key" "value"} (plist/parse result))))))

(deftest test-as-binary-empty-map
  (testing "as-binary encodes empty map"
    (let [result (plist/as-binary {})]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= {} (plist/parse result))))))

(deftest test-as-binary-map-with-multiple-keys
  (testing "as-binary encodes map with multiple keys"
    (let [data {"name" "John" "age" 30}
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

(deftest test-as-binary-nested-map
  (testing "as-binary encodes nested map"
    (let [data {"outer" {"inner" "value"}}
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

(deftest test-as-binary-map-with-mixed-types
  (testing "as-binary encodes map with mixed value types"
    (let [data {"string" "text"
                "number" 123}
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

(deftest test-as-binary-complex-nested-structure
  (testing "as-binary encodes complex nested structure"
    (let [data {"user" {"name" "Alice"
                        "profile" {"email" "alice@example.com"
                                   "age" 25}}
                "settings" {"theme" "dark"}}
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

;; Round-trip tests

(deftest test-round-trip-integers
  (testing "round-trip encoding and parsing of integers"
    (doseq [value [0 1 42 -1 100000 12345678]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Round-trip failed for value: " value))))))

(deftest test-round-trip-strings
  (testing "round-trip encoding and parsing of strings"
    (doseq [value ["" "test" "hello world" "a very long string with lots of characters to test length encoding"]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Round-trip failed for value: " value))))))

(deftest test-round-trip-maps
  (testing "round-trip encoding and parsing of maps"
    (doseq [value [{}
                   {"a" "b"}
                   {"x" 1 "y" 2}
                   {"nested" {"key" "value"}}
                   {"mixed" {"str" "text" "num" 42}}]]
      (let [encoded (plist/as-binary value)
            decoded (plist/parse encoded)]
        (is (= value decoded)
            (str "Round-trip failed for value: " value))))))

;; Edge case tests

(deftest test-parse-invalid-format
  (testing "parse with string that doesn't match any format"
    (is (thrown? js/Error (plist/parse "invalid format string")))))

(deftest test-parse-empty-string
  (testing "parse with empty string"
    (is (thrown? js/Error (plist/parse "")))))

(deftest test-as-binary-map-with-large-number-of-keys
  (testing "as-binary encodes map with > 14 keys (tests length encoding)"
    (let [data (into {} (map #(vector (str "key" %) (str "value" %))) (range 20))
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

(deftest test-as-binary-with-duplicate-values
  (testing "as-binary efficiently handles duplicate values"
    (let [data {"key1" "shared"
                "key2" "shared"
                "key3" "shared"
                "key4" "unique"}
          result (plist/as-binary data)]
      (is (string? result))
      (is (.startsWith result "bplist"))
      ;; Verify we can parse it back
      (is (= data (plist/parse result))))))

;; Integration test for vector clock use case

(deftest test-vector-clock-serialization
  (testing "Encoding and decoding a vector clock structure"
    (let [clock {"device-1" 5 "device-2" 3 "device-3" 10}
          encoded (plist/as-binary clock)
          decoded (plist/parse encoded)]
      (is (= clock decoded)))))
