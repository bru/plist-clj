(ns plist.plutil
  "Development helpers for validating plist encoding/decoding using macOS plutil.

   IMPORTANT: This namespace is Node.js-only and requires macOS with plutil installed.
   It is not intended for production use or browser environments."
  (:require [plist.core :as plist]
            ["child_process" :as cp]
            ["fs" :as fs]
            ["path" :as path]
            ["os" :as os]))

;; Platform Detection

(defn plutil-available?
  "Check if plutil is available on this system (macOS + Node.js only)."
  []
  (and (= "darwin" (.-platform os))
       (try
         (.execSync cp "which plutil")
         true
         (catch :default _ false))))

;; File Utilities

(defn- temp-file-path
  "Generate a temporary file path for plist operations."
  [prefix]
  (path/join (.tmpdir os) (str prefix "-" (.toString (.randomBytes (js/require "crypto") 8) "hex") ".plist")))

(defn- write-temp-file
  "Write binary data to a temporary file and return the path."
  [binary-buffer]
  (let [tmpfile (temp-file-path "plist-test")]
    (.writeFileSync fs tmpfile binary-buffer "binary")
    tmpfile))

(defn- cleanup-file
  "Delete a file if it exists."
  [filepath]
  (when (.existsSync fs filepath)
    (.unlinkSync fs filepath)))

;; Validation Helpers

(defn validate-binary
  "Validate a binary plist buffer using plutil -lint.

   Returns a map with:
   - :valid? - boolean indicating if the plist is valid
   - :output - string output from plutil
   - :error - error message if validation failed"
  [binary-buffer]
  (let [tmpfile (write-temp-file binary-buffer)]
    (try
      (let [result (.execSync cp (str "plutil -lint " tmpfile) #js {:encoding "utf8"})]
        {:valid? true
         :output (str result)})
      (catch :default e
        {:valid? false
         :output (or (aget e "stdout") "")
         :error (.-message e)})
      (finally
        (cleanup-file tmpfile)))))

(defn convert-to-json
  "Convert a binary plist buffer to JSON using plutil.

   Returns a map with:
   - :success? - boolean indicating if conversion succeeded
   - :json - parsed JSON object if successful
   - :json-str - raw JSON string
   - :error - error message if conversion failed"
  [binary-buffer]
  (let [tmpfile (write-temp-file binary-buffer)]
    (try
      (let [result (.execSync cp (str "plutil -convert json " tmpfile " -o -") #js {:encoding "utf8"})
            json-str (str result)]
        {:success? true
         :json-str json-str
         :json (js->clj (js/JSON.parse json-str) :keywordize-keys true)})
      (catch :default e
        {:success? false
         :error (.-message e)})
      (finally
        (cleanup-file tmpfile)))))

(defn convert-to-xml
  "Convert a binary plist buffer to XML using plutil.

   Returns a map with:
   - :success? - boolean indicating if conversion succeeded
   - :xml - XML string if successful
   - :error - error message if conversion failed"
  [binary-buffer]
  (let [tmpfile (write-temp-file binary-buffer)]
    (try
      (let [result (.execSync cp (str "plutil -convert xml1 " tmpfile " -o -") #js {:encoding "utf8"})]
        {:success? true
         :xml (str result)})
      (catch :default e
        {:success? false
         :error (.-message e)})
      (finally
        (cleanup-file tmpfile)))))

;; Round-Trip Testing

(defn round-trip-via-plutil
  "Test round-trip encoding/decoding through plutil.

   Process:
   1. Encode data with plist-clj
   2. Convert to JSON with plutil (validation step)
   3. Decode back with plist-clj

   Returns a map with:
   - :success? - boolean indicating if round-trip succeeded
   - :original - the original data
   - :final - the final decoded data
   - :matches? - whether original equals final
   - :json - intermediate JSON from plutil
   - :error - error message if any step failed"
  [data]
  (try
    (let [binary (plist/as-binary data)
          json-result (convert-to-json binary)]
      (if (:success? json-result)
        (let [final (plist/parse binary)]
          {:success? true
           :original data
           :final final
           :matches? (= data final)
           :json (:json json-result)})
        {:success? false
         :original data
         :error (str "plutil conversion failed: " (:error json-result))}))
    (catch :default e
      {:success? false
       :original data
       :error (.-message e)})))

;; Bidirectional Testing

(defn test-encode-compatibility
  "Test that plist-clj encoding is compatible with plutil.

   Validates the binary plist and converts to JSON to verify plutil can read it.

   Returns a map with:
   - :valid? - boolean indicating if encoding is plutil-compatible
   - :lint-result - result from plutil -lint
   - :json-result - result from plutil convert to JSON
   - :error - error message if validation failed"
  [data]
  (try
    (let [binary (plist/as-binary data)
          lint (validate-binary binary)]
      (if (:valid? lint)
        (let [json (convert-to-json binary)]
          {:valid? (:success? json)
           :lint-result lint
           :json-result json})
        {:valid? false
         :lint-result lint
         :error "plutil lint failed"}))
    (catch :default e
      {:valid? false
       :error (.-message e)})))

(defn create-with-plutil
  "Create a binary plist using plutil for comparison.

   NOTE: This is limited to simple data structures that can be created via plutil CLI.
   For complex structures, it's better to use fixture files.

   Returns a Uint8Array binary plist buffer."
  [data]
  (let [tmpfile (temp-file-path "plutil-create")]
    (try
      ;; Create empty binary plist
      (.execSync cp (str "plutil -create binary1 " tmpfile))

      ;; TODO: Add data insertion logic using plutil -insert
      ;; This is complex for nested structures, so we'll keep it simple for now

      (let [binary (.readFileSync fs tmpfile)]
        binary)
      (finally
        (cleanup-file tmpfile)))))

;; File Operations

(defn save-for-inspection
  "Save binary plist to a file for manual inspection.

   Args:
   - data: Clojure data structure to encode
   - filename: Output filename (without extension, .plist will be added)

   Returns the full path to the saved file."
  [data filename]
  (let [binary (plist/as-binary data)
        filepath (if (.endsWith filename ".plist")
                   filename
                   (str filename ".plist"))]
    (.writeFileSync fs filepath binary "binary")
    filepath))

(defn validate-file
  "Validate a plist file using plutil -lint.

   Returns same format as validate-binary."
  [filepath]
  (try
    (let [result (.execSync cp (str "plutil -lint " filepath) #js {:encoding "utf8"})]
      {:valid? true
       :output (str result)})
    (catch :default e
      {:valid? false
       :output (or (aget e "stdout") "")
       :error (.-message e)})))

;; Pretty Printing for REPL

(defn inspect
  "Pretty-print validation results for REPL use."
  [result]
  (println "\n=== Validation Result ===")
  (doseq [[k v] result]
    (println (str (name k) ":") v))
  (println "========================\n")
  result)

;; High-level API

(defn verify
  "High-level verification function that runs all checks.

   Returns a comprehensive report with:
   - :data - original data
   - :encoding - whether plist-clj can encode the data
   - :plutil-compatible - whether plutil can read the encoded data
   - :round-trip - whether data survives encode/decode cycle
   - :json-representation - JSON view of the data via plutil"
  [data]
  (println (str "\nVerifying: " (pr-str data)))
  (let [report (atom {:data data})]

    ;; Test encoding
    (try
      (let [binary (plist/as-binary data)]
        (swap! report assoc :encoding {:success? true :size (.-length binary)})

        ;; Test plutil compatibility
        (let [compat (test-encode-compatibility data)]
          (swap! report assoc :plutil-compatible compat))

        ;; Test round-trip
        (let [rt (round-trip-via-plutil data)]
          (swap! report assoc :round-trip rt)))
      (catch :default e
        (swap! report assoc :encoding {:success? false :error (.-message e)})))

    (inspect @report)))
