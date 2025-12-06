(ns plist.fixture-test
  "Integration tests using plutil-generated fixture files.

   These tests verify that plist-clj can correctly decode binary plist files
   that are validated by Apple's plutil utility."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [plist.core :as plist]
            ["fs" :as fs]
            ["path" :as path]))

;; Fixture directory and helper functions

(def fixture-dir
  "Path to the fixtures directory - use absolute path from process.cwd()"
  (path/join (js/process.cwd) "test" "fixtures"))

(defn read-fixture
  "Read a binary plist fixture file"
  [filename]
  (.readFileSync fs (path/join fixture-dir filename) "binary"))

(defn test-fixture
  "Test that a fixture can be decoded and matches expected data"
  [filename expected-data]
  (let [binary (read-fixture filename)
        decoded (plist/parse binary)]
    (testing (str "decoding " filename)
      (is (= expected-data decoded)
          (str "Decoded data should match expected for " filename)))

    (testing (str "round-trip " filename)
      (let [reencoded (plist/as-binary decoded)
            redecoded (plist/parse reencoded)]
        (is (= decoded redecoded)
            (str "Round-trip should preserve data for " filename))))))

;; Test fixtures

(deftest test-basic-types-fixture
  (test-fixture "basic-types.plist"
                {"integer" 42
                 "string" "hello"
                 "bool-true" true
                 "bool-false" false
                 "nil" nil}))

(deftest test-boundary-integers-fixture
  (test-fixture "boundary-integers.plist"
                {"byte-max" 255
                 "short-min" 256
                 "short-max" 65535
                 "int-min" 65536
                 "int-max" 4294967295
                 "long-min" 4294967296
                 "negative-small" -1
                 "negative-large" -9223372036854775808}))

(deftest test-boundary-strings-fixture
  (test-fixture "boundary-strings.plist"
                {"empty" ""
                 "single" "a"
                 "short" "hello world"
                 "len-13" "thirteen-char"
                 "len-14" "fourteen-chars"
                 "len-15" "fifteen--chars!"
                 "len-16" "sixteen--chars!!"
                 "long" "This is a much longer string that exceeds the inline length encoding threshold and requires extended length markers in the binary plist format."}))

(deftest test-boundary-maps-fixture
  (let [make-map (fn [n] (into {} (map (fn [i] [(str "key" i) i]) (range n))))
        expected {"empty" {}
                  "single" {"a" 1}
                  "small" {"a" 1 "b" 2 "c" 3}
                  "len-13" (make-map 13)
                  "len-14" (make-map 14)
                  "len-15" (make-map 15)
                  "len-16" (make-map 16)
                  "large" (make-map 50)}]
    (test-fixture "boundary-maps.plist" expected)))

(deftest test-nested-structures-fixture
  (test-fixture "nested-structures.plist"
                {"level-1" {"level-2" {"level-3" {"level-4" {"level-5" "deep value"}}}}
                 "mixed" {"users" {"alice" {"age" 30 "active" true}
                                   "bob" {"age" 25 "active" false}}
                          "count" 2
                          "enabled" true}}))

(deftest test-empty-collections-fixture
  (test-fixture "empty-collections.plist"
                {"empty-map" {}
                 "map-with-empty" {"outer" {"inner" {}}}}))

(deftest test-complex-mixed-fixture
  (test-fixture "complex-mixed.plist"
                {"name" "Sample Configuration"
                 "version" 1
                 "enabled" true
                 "settings" {"timeout" 30
                             "retries" 3
                             "verbose" false}
                 "endpoints" {"primary" "https://api.example.com"
                              "fallback" "https://backup.example.com"}
                 "metadata" {"created" "2025-01-01"
                             "author" "dev-team"
                             "tags" {"environment" "production"
                                     "region" "us-west"}}}))

;; Comprehensive round-trip test

(deftest test-all-fixtures-exist
  (testing "all expected fixture files exist"
    (let [expected-files ["basic-types.plist"
                          "boundary-integers.plist"
                          "boundary-strings.plist"
                          "boundary-maps.plist"
                          "nested-structures.plist"
                          "empty-collections.plist"
                          "complex-mixed.plist"]]
      (doseq [filename expected-files]
        (is (.existsSync fs (path/join fixture-dir filename))
            (str "Fixture " filename " should exist"))))))

(deftest test-all-fixtures-start-with-bplist
  (testing "all fixtures are binary plist format"
    (let [fixtures ["basic-types.plist"
                    "boundary-integers.plist"
                    "boundary-strings.plist"
                    "boundary-maps.plist"
                    "nested-structures.plist"
                    "empty-collections.plist"
                    "complex-mixed.plist"]]
      (doseq [filename fixtures]
        (let [binary (read-fixture filename)]
          (is (.startsWith binary "bplist")
              (str "Fixture " filename " should start with 'bplist'")))))))
