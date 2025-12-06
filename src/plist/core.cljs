(ns plist.core
  "Binary plist (property list) parser and encoder.

  Provides encoding and decoding of Apple's binary plist format, which is used
  for efficient serialization of structured data. This implementation supports
  a subset of the plist specification, focusing on the types needed for vector
  clock serialization: strings, integers, and dictionaries (maps)."
  (:require [clojure.string :as s]
            [plist.decode :as decode]
            [plist.encode :as encode]))

(defn parse
  "Parse a plist string and return the decoded data structure.

  Supports binary plist format (starting with 'bplist'). XML and JSON formats
  are recognized but not implemented.

  Example:
  ```clojure
  (def binary-plist \"bplist00...\")
  (parse binary-plist) ; => {\"key\" \"value\"}
  ```"
  [plist]
  (condp #(s/starts-with? %2 %1) plist
    "bplist" (decode/parse plist)
    "<?xml " (throw (js/Error. "XML plist parsing not implemented"))
    "{" (throw (js/Error. "JSON plist parsing not implemented"))
    (throw (js/Error. (str "Unknown plist format: " (subs plist 0 (min 10 (count plist))))))))

(defn as-binary
  "Encode a Clojure data structure as a binary plist string.

  Supports encoding of: nil, booleans, integers, strings, and maps.
  Arrays, dates, and real numbers are not yet supported.

  Example:
  ```clojure
  (as-binary {\"device-1\" 5}) ; => \"bplist00...\"
  (as-binary \"hello\") ; => \"bplist00...\"
  ```"
  [data]
  (encode/write data))