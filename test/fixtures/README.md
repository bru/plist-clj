# Test Fixtures

This directory contains binary plist files used for integration testing. These fixtures verify that `plist-clj` produces binary plists compatible with Apple's `plutil` utility.

## Fixtures

All fixtures were generated using `plist-clj` and validated with macOS's `plutil` command.

### basic-types.plist (113 bytes)

**Description**: Basic data types: integer, string, boolean, nil

**Expected data**:
```clojure
{"integer" 42
 "string" "hello"
 "bool-true" true
 "bool-false" false
 "nil" nil}
```

**Validates**: Core data types encoding

### boundary-integers.plist (203 bytes)

**Description**: Integer values at boundary points between different encoding sizes

**Expected data**:
```clojure
{"byte-max" 255                    ; Max 1-byte (marker 0x10)
 "short-min" 256                   ; Min 2-byte (marker 0x11)
 "short-max" 65535                 ; Max 2-byte (marker 0x11)
 "int-min" 65536                   ; Min 4-byte (marker 0x12)
 "int-max" 4294967295              ; Max 4-byte (marker 0x12)
 "long-min" 4294967296             ; Min 8-byte (marker 0x13)
 "negative-small" -1               ; Small negative
 "negative-large" -9223372036854775808} ; Min 8-byte signed
```

**Validates**: Integer encoding at type marker boundaries

### boundary-strings.plist (370 bytes)

**Description**: Strings at length encoding boundaries

**Expected data**:
```clojure
{"empty" ""
 "single" "a"
 "short" "hello world"
 "len-13" "thirteen-char"          ; 13 chars - max inline length
 "len-14" "fourteen-chars"         ; 14 chars - exactly at boundary
 "len-15" "fifteen--chars!"        ; 15 chars - first extended length
 "len-16" "sixteen--chars!!"       ; 16 chars
 "long" "This is a much longer string..."}
```

**Validates**: String length encoding (inline vs extended length markers)

### boundary-maps.plist (984 bytes)

**Description**: Maps at count encoding boundaries

**Expected data**:
```clojure
{"empty" {}
 "single" {"a" 1}
 "small" {"a" 1 "b" 2 "c" 3}
 "len-13" {13 key-value pairs}     ; 13 keys - max inline count
 "len-14" {14 key-value pairs}     ; 14 keys - exactly at boundary
 "len-15" {15 key-value pairs}     ; 15 keys - first extended count
 "len-16" {16 key-value pairs}
 "large" {50 key-value pairs}}
```

**Validates**: Map size encoding (inline vs extended count markers)

### nested-structures.plist (213 bytes)

**Description**: Deeply nested and complex structures

**Expected data**:
```clojure
{"level-1" {"level-2" {"level-3" {"level-4" {"level-5" "deep value"}}}}
 "mixed" {"users" {"alice" {"age" 30 "active" true}
                   "bob" {"age" 25 "active" false}}
          "count" 2
          "enabled" true}}
```

**Validates**: Deep nesting and mixed type structures

### empty-collections.plist (97 bytes)

**Description**: Various empty collections

**Expected data**:
```clojure
{"empty-map" {}
 "map-with-empty" {"outer" {"inner" {}}}}
```

**Validates**: Empty map encoding and nested empty maps

### complex-mixed.plist (397 bytes)

**Description**: Real-world-like complex structure

**Expected data**:
```clojure
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
                     "region" "us-west"}}}
```

**Validates**: Real-world configuration-like structures

## How These Fixtures Were Created

These fixtures were generated using the development tools in `dev/plist/`:

```bash
# From project root
npx shadow-cljs compile gen-fixtures
node out/gen-fixtures.js
```

Or interactively in a REPL:

```clojure
(require '[plist.fixtures :as fixtures])
(fixtures/generate-all-fixtures!)
```

Each fixture was:
1. Encoded using `plist.core/as-binary`
2. Written to file as binary data
3. Validated using `plutil -lint`
4. Round-trip tested (decode → encode → decode)

## Validating Fixtures Manually

You can validate these fixtures using macOS's `plutil` command:

```bash
# Validate all fixtures
plutil -lint test/fixtures/*.plist

# Convert to JSON for inspection
plutil -convert json test/fixtures/basic-types.plist -o -

# Convert to XML for inspection
plutil -convert xml1 test/fixtures/basic-types.plist -o -

# Pretty-print
plutil -p test/fixtures/basic-types.plist
```

## Regenerating Fixtures

If you need to regenerate the fixtures (e.g., after format changes):

### Using the generator script

```bash
npx shadow-cljs compile gen-fixtures
node out/gen-fixtures.js
```

### Using the dev tools interactively

```bash
# Start REPL
npx shadow-cljs cljs-repl node-test

# In the REPL
(require '[plist.fixtures :as fixtures])

# Generate all fixtures
(fixtures/generate-all-fixtures!)

# Or generate a specific fixture
(fixtures/generate-fixture! :basic-types fixtures/basic-types)

# Validate all fixtures
(fixtures/validate-all-fixtures!)
```

## Binary Plist Format Reference

These fixtures test various aspects of Apple's binary plist format:

- **Header**: `bplist00` (8 bytes)
- **Object Types**:
  - `0x00` - null/nil
  - `0x08` - false
  - `0x09` - true
  - `0x1N` - integer (N = byte count: 0=1byte, 1=2byte, 2=4byte, 3=8byte)
  - `0x5N` - ASCII string (N < 15 = inline length, N = 15 = extended length)
  - `0xDN` - dictionary/map (N < 15 = inline count, N = 15 = extended count)
- **Offset Table**: Array of offsets to each object
- **Trailer**: 32 bytes containing metadata

For full specification, see:
- [Apple's CFBinaryPList.c](https://opensource.apple.com/source/CF/CF-550/CFBinaryPList.c)
- [Binary Property List Format Spec](https://medium.com/@karaiskc/understanding-apples-binary-property-list-format-281e6da00dbd)

## See Also

- [plist.core-test.cljs](../plist/core_test.cljs) - Round-trip and general tests
- [plist.boundary-test.cljs](../plist/boundary_test.cljs) - Boundary value tests
- [plist.fixture-test.cljs](../plist/fixture_test.cljs) - Fixture-based integration tests
- [dev/README.md](../../dev/README.md) - Developer tools documentation
