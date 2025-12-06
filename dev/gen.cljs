(ns gen
  (:require [plist.core :as plist]
            [plist.fixtures :as fixtures]
            ["fs" :as fs]
            ["path" :as path]
            ["child_process" :as cp]))

(enable-console-print!)

(defn generate-and-validate-fixture [name data description]
  (let [filename (str (clojure.core/name name) ".plist")
        filepath (path/join "test/fixtures" filename)
        binary (plist/as-binary data)]

    (println (str "\nGenerating " filename "..."))
    ;; Write as binary encoding to preserve byte values
    (.writeFileSync fs filepath binary "binary")
    (println (str "  Written: " (count binary) " bytes"))

    ;; Validate with plutil
    (try
      (.execSync cp (str "plutil -lint " filepath))
      (println "  ✓ plutil validation passed")
      (catch :default e
        (println (str "  ✗ plutil validation failed: " (.-message e)))))

    ;; Verify round-trip
    (let [decoded (plist/parse binary)
          matches? (= decoded data)]
      (if matches?
        (println "  ✓ Round-trip successful")
        (println "  ✗ Round-trip failed - decoded data doesn't match")))))

(defn main []
  (println "\n=== Generating Test Fixtures ===")

  (.mkdirSync fs "test/fixtures" #js {:recursive true})

  (doseq [[name {:keys [data description]}] fixtures/all-fixtures]
    (generate-and-validate-fixture name data description))

  (println "\n=== Generation Complete ===\n"))

(main)
