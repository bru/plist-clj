# Developer Tools for plist-clj

This directory contains development tools for validating plist encoding/decoding using macOS's `plutil` utility.

## Requirements

- **macOS** with `plutil` installed (standard on all macOS versions)
- **Node.js** environment
- These tools are **not** browser-compatible (they use Node.js modules like `child_process`, `fs`, `path`)

## Overview

The dev tools provide two main namespaces:

1. **`plist.plutil`** - Helper functions for validating plist files with plutil
2. **`plist.fixtures`** - Tools for generating and validating test fixtures

## Getting Started

```bash
npx shadow-cljs node-repl
```

Then in the REPL:

```clojure
(require '[plist.plutil :as plutil])
(require '[plist.fixtures :as fixtures])
(require '[plist.core :as plist])

(plutil/test-encode-compatibility {"test" "data"})
(fixtures/generate-all-fixtures!)
```

### Quick Script Execution

```bash
npm run gen-fixtures
```

## Usage Examples

### Interactive Validation

```clojure
(require '[plist.plutil :as plutil])
(require '[plist.core :as plist])

;; Check if plutil is available
(plutil/plutil-available?)
;; => true

;; Test encoding compatibility
(def data {:name "John" :age 30 :active true})
(plutil/test-encode-compatibility data)
;; => {:valid? true
;;     :lint-result {...}
;;     :json-result {...}}

;; Round-trip test via plutil
(plutil/round-trip-via-plutil data)
;; => {:success? true
;;     :original {:name "John" :age 30 :active true}
;;     :final {:name "John" :age 30 :active true}
;;     :matches? true
;;     :json {...}}

;; High-level verification (runs all checks)
(plutil/verify {:foo "bar" :baz 42})
;; Prints comprehensive report and returns detailed results

;; Validate a binary plist
(def binary (plist/as-binary {:test "data"}))
(plutil/validate-binary binary)
;; => {:valid? true :output "..."}

;; Convert to JSON for inspection
(plutil/convert-to-json binary)
;; => {:success? true :json {:test "data"} :json-str "{...}"}

;; Convert to XML for inspection
(plutil/convert-to-xml binary)
;; => {:success? true :xml "<?xml version=\"1.0\"..."}

;; Save for manual inspection
(plutil/save-for-inspection {:my "data"} "output")
;; => "output.plist"
;; Then inspect with: plutil -p output.plist
```

### Generating Test Fixtures

```clojure
(require '[plist.fixtures :as fixtures])

;; List available fixture definitions
(fixtures/list-fixtures)
;; Prints list of all predefined fixtures

;; Generate all test fixtures
(fixtures/generate-all-fixtures!)
;; Creates .plist files in test/fixtures/
;; Validates each with plutil
;; Returns map of results

;; Generate a specific fixture
(fixtures/generate-fixture! :basic-types fixtures/basic-types)
;; => {:success? true
;;     :filepath "test/fixtures/basic-types.plist"
;;     :size 123
;;     :validation {:valid? true ...}}

;; Generate a custom fixture
(fixtures/generate-fixture! "my-custom" {:custom "data" :value 123})

;; Validate all fixtures
(fixtures/validate-all-fixtures!)
;; Checks that each fixture:
;; 1. Passes plutil validation
;; 2. Decodes to expected data

;; Validate a specific fixture
(fixtures/validate-fixture :basic-types fixtures/basic-types)
;; => {:valid? true
;;     :filepath "test/fixtures/basic-types.plist"
;;     :decoded {...}
;;     :matches? true
;;     :plutil-valid? true}

;; Test round-trip for a fixture
(fixtures/test-fixture-roundtrip :basic-types)
;; => {:success? true
;;     :original-size 234
;;     :reencoded-size 234
;;     :matches? true}

;; Quick test (generate + validate + round-trip all fixtures)
(fixtures/quick-test)
;; Runs complete test suite and prints results
```

### Predefined Fixture Data

The `plist.fixtures` namespace includes these predefined test data sets:

- **`basic-types`** - Integer, string, boolean, nil
- **`boundary-integers`** - Values at encoding size boundaries (255, 256, 65535, etc.)
- **`boundary-strings`** - Strings at length encoding boundaries (13, 14, 15 chars)
- **`boundary-maps`** - Maps at count encoding boundaries (13, 14, 15 keys)
- **`nested-structures`** - Deeply nested maps
- **`empty-collections`** - Various empty maps
- **`complex-mixed`** - Real-world-like complex structure

Access them directly:

```clojure
fixtures/basic-types
;; => {:integer 42 :string "hello" :bool-true true :bool-false false :nil nil}

fixtures/boundary-integers
;; => {:byte-max 255 :short-min 256 :short-max 65535 ...}
```

## Manual Testing with plutil

After generating fixtures, you can test them manually:

```bash
# Validate a fixture
plutil -lint test/fixtures/basic-types.plist

# Convert to JSON for inspection
plutil -convert json test/fixtures/basic-types.plist -o -

# Convert to XML for inspection
plutil -convert xml1 test/fixtures/basic-types.plist -o -

# Pretty-print for reading
plutil -p test/fixtures/basic-types.plist
```

## See Also

- [Main README](../README.md) - Project documentation
- [Test Fixtures README](../test/fixtures/README.md) - Documentation of generated fixtures
