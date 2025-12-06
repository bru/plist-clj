(ns plist.fixtures
  "Helper functions to generate and validate test fixtures using plutil.

   IMPORTANT: This namespace is Node.js-only and requires macOS with plutil installed."
  (:require [plist.core :as plist]
            [plist.plutil :as plutil]
            ["fs" :as fs]
            ["path" :as path]))

(def fixtures-dir "test/fixtures")

(plutil/test-encode-compatibility {"test" "data"})

;; Fixture Data Definitions

(def basic-types
  "Basic data types: integer, string, boolean, nil"
  {"integer" 42
   "string" "hello"
   "bool-true" true
   "bool-false" false
   "nil" nil})

(def boundary-integers
  "Integer values at boundary points between different encoding sizes"
  {"byte-max" 255                    ; Max 1-byte (0x10)
   "short-min" 256                   ; Min 2-byte (0x11)
   "short-max" 65535                 ; Max 2-byte (0x11)
   "int-min" 65536                   ; Min 4-byte (0x12)
   "int-max" 4294967295              ; Max 4-byte (0x12)
   "long-min" 4294967296             ; Min 8-byte (0x13)
   "negative-small" -1               ; Small negative
   "negative-large" -9223372036854775808}) ; Min 8-byte signed

(def boundary-strings
  "Strings at boundary points for length encoding"
  {"empty" ""
   "single" "a"
   "short" "hello world"
   "len-13" "thirteen-char"          ; 13 chars - max inline length
   "len-14" "fourteen-chars"         ; 14 chars - exactly at boundary
   "len-15" "fifteen--chars!"        ; 15 chars - first extended length
   "len-16" "sixteen--chars!!"       ; 16 chars
   "long" "This is a much longer string that exceeds the inline length encoding threshold and requires extended length markers in the binary plist format."})

(def boundary-maps
  "Maps at boundary points for dict size encoding"
  (let [make-map (fn [n]
                   (into {} (map (fn [i] [(str "key" i) i]) (range n))))]
    {"empty" {}
     "single" {"a" 1}
     "small" {"a" 1 "b" 2 "c" 3}
     "len-13" (make-map 13)          ; 13 keys - max inline count
     "len-14" (make-map 14)          ; 14 keys - exactly at boundary
     "len-15" (make-map 15)          ; 15 keys - first extended count
     "len-16" (make-map 16)
     "large" (make-map 50)}))

(def nested-structures
  "Deeply nested and complex structures"
  {"level-1" {"level-2" {"level-3" {"level-4" {"level-5" "deep value"}}}}
   "mixed" {"users" {"alice" {"age" 30 "active" true}
                     "bob" {"age" 25 "active" false}}
            "count" 2
            "enabled" true}})

(def empty-collections
  "Various empty collections"
  {"empty-map" {}
   "map-with-empty" {"outer" {"inner" {}}}})

(def complex-mixed
  "Real-world-like complex structure"
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
                       "region" "us-west"}}})

;; Fixture metadata mapping

(def all-fixtures
  "Map of fixture names to their data and descriptions"
  {:basic-types       {:data basic-types
                       :description "Basic data types: integer, string, boolean, nil"}
   :boundary-integers {:data boundary-integers
                       :description "Integer boundary values for encoding sizes"}
   :boundary-strings  {:data boundary-strings
                       :description "String length boundaries for encoding"}
   :boundary-maps     {:data boundary-maps
                       :description "Map size boundaries for encoding"}
   :nested-structures {:data nested-structures
                       :description "Deeply nested and complex structures"}
   :empty-collections {:data empty-collections
                       :description "Various empty collections"}
   :complex-mixed     {:data complex-mixed
                       :description "Real-world-like complex structure"}})

;; Fixture Generation

(defn ensure-fixtures-dir!
  "Ensure the fixtures directory exists."
  []
  (when-not (.existsSync fs fixtures-dir)
    (.mkdirSync fs fixtures-dir #js {:recursive true})))

(defn generate-fixture!
  "Generate a fixture file by encoding data with plist-clj.

   Args:
   - name: fixture name (string or keyword)
   - data: Clojure data structure to encode

   Returns a map with:
   - :success? - whether generation succeeded
   - :filepath - path to generated file
   - :validation - result from plutil validation
   - :error - error message if generation failed"
  [name data]
  (ensure-fixtures-dir!)
  (let [filename (str (clojure.core/name name) ".plist")
        filepath (path/join fixtures-dir filename)]
    (try
      (let [binary (plist/as-binary data)]
        ;; Write the binary plist
        (.writeFileSync fs filepath binary)

        ;; Validate with plutil
        (let [validation (plutil/validate-file filepath)]
          (if (:valid? validation)
            {:success? true
             :filepath filepath
             :size (.-length binary)
             :validation validation}
            {:success? false
             :filepath filepath
             :validation validation
             :error "Generated plist failed plutil validation"})))
      (catch :default e
        {:success? false
         :filepath filepath
         :error (.-message e)}))))

(defn generate-all-fixtures!
  "Generate all predefined test fixtures.

   Returns a map of fixture names to generation results."
  []
  (println "\n=== Generating Test Fixtures ===\n")
  (let [results (into {}
                      (map (fn [[name {:keys [data description]}]]
                             (println (str "Generating " (clojure.core/name name) "..."))
                             (let [result (generate-fixture! name data)]
                               (if (:success? result)
                                 (println (str "  ✓ Success: " (:filepath result) " (" (:size result) " bytes)"))
                                 (println (str "  ✗ Failed: " (:error result))))
                               [name result]))
                           all-fixtures))]
    (println "\n=== Generation Complete ===\n")
    (println (str "Success: " (count (filter #(:success? (val %)) results)) "/" (count results)))
    results))

;; Fixture Validation

(defn validate-fixture
  "Validate that a fixture file decodes to the expected data.

   Args:
   - fixture-name: name of the fixture (keyword or string)
   - expected-data: expected Clojure data structure

   Returns a map with:
   - :valid? - whether validation succeeded
   - :filepath - path to fixture file
   - :decoded - decoded data from fixture
   - :matches? - whether decoded data matches expected
   - :plutil-valid? - whether plutil validates the file
   - :error - error message if validation failed"
  [fixture-name expected-data]
  (let [filename (str (clojure.core/name fixture-name) ".plist")
        filepath (path/join fixtures-dir filename)]
    (if (.existsSync fs filepath)
      (try
        ;; Validate with plutil
        (let [plutil-result (plutil/validate-file filepath)
              plutil-valid? (:valid? plutil-result)]

          ;; Decode with plist-clj
          (let [binary (.readFileSync fs filepath)
                decoded (plist/parse binary)
                matches? (= decoded expected-data)]

            {:valid? (and plutil-valid? matches?)
             :filepath filepath
             :decoded decoded
             :expected expected-data
             :matches? matches?
             :plutil-valid? plutil-valid?
             :plutil-result plutil-result}))
        (catch :default e
          {:valid? false
           :filepath filepath
           :error (.-message e)}))
      {:valid? false
       :filepath filepath
       :error "Fixture file does not exist"})))

(defn validate-all-fixtures!
  "Validate all generated fixture files against their expected data.

   Returns a map of fixture names to validation results."
  []
  (println "\n=== Validating Test Fixtures ===\n")
  (let [results (into {}
                      (map (fn [[name {:keys [data description]}]]
                             (println (str "Validating " (clojure.core/name name) "..."))
                             (let [result (validate-fixture name data)]
                               (if (:valid? result)
                                 (println "  ✓ Valid: matches expected data and passes plutil")
                                 (do
                                   (println (str "  ✗ Invalid: " (:error result)))
                                   (when-not (:matches? result)
                                     (println "    - Decoded data does not match expected"))
                                   (when-not (:plutil-valid? result)
                                     (println "    - Failed plutil validation"))))
                               [name result]))
                           all-fixtures))]
    (println "\n=== Validation Complete ===\n")
    (println (str "Valid: " (count (filter #(:valid? (val %)) results)) "/" (count results)))
    results))

;; Round-trip Testing

(defn test-fixture-roundtrip
  "Test that a fixture can be decoded and re-encoded correctly.

   Returns a map with:
   - :success? - whether round-trip succeeded
   - :original-size - size of original fixture file
   - :reencoded-size - size of re-encoded data
   - :matches? - whether decoded data equals re-decoded data"
  [fixture-name]
  (let [filename (str (clojure.core/name fixture-name) ".plist")
        filepath (path/join fixtures-dir filename)]
    (if (.existsSync fs filepath)
      (try
        (let [original-binary (.readFileSync fs filepath)
              decoded (plist/parse original-binary)
              reencoded (plist/as-binary decoded)
              redecoded (plist/parse reencoded)]
          {:success? (= decoded redecoded)
           :filepath filepath
           :original-size (.-length original-binary)
           :reencoded-size (.-length reencoded)
           :decoded decoded
           :redecoded redecoded
           :matches? (= decoded redecoded)})
        (catch :default e
          {:success? false
           :filepath filepath
           :error (.-message e)}))
      {:success? false
       :filepath filepath
       :error "Fixture file does not exist"})))

;; Helper for getting expected data

(defn get-expected-data
  "Get the expected data for a fixture by name."
  [fixture-name]
  (get-in all-fixtures [(keyword fixture-name) :data]))

(defn list-fixtures
  "List all available fixtures with their descriptions."
  []
  (println "\n=== Available Fixtures ===\n")
  (doseq [[name {:keys [description]}] all-fixtures]
    (println (str (clojure.core/name name) ":"))
    (println (str "  " description)))
  (println))

;; Quick test function

(defn quick-test
  "Quick test to generate, validate, and test round-trip for all fixtures."
  []
  (when-not (plutil/plutil-available?)
    (println "ERROR: plutil not available. This tool requires macOS.")
    (js/process.exit 1))

  (println "\n==========================================")
  (println "  plist-clj Fixture Generation Test")
  (println "==========================================")

  (let [gen-results (generate-all-fixtures!)
        val-results (validate-all-fixtures!)]

    (println "\n=== Round-Trip Tests ===\n")
    (doseq [name (keys all-fixtures)]
      (println (str "Testing " (clojure.core/name name) " round-trip..."))
      (let [result (test-fixture-roundtrip name)]
        (if (:success? result)
          (println (str "  ✓ Success (original: " (:original-size result) "b, reencoded: " (:reencoded-size result) "b)"))
          (println (str "  ✗ Failed: " (:error result))))))

    (println "\n==========================================")))
