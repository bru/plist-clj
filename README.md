# plist-clj

Binary plist encoder/decoder for ClojureScript (Clojure support coming "soon").

[![Clojars](https://img.shields.io/clojars/v/net.clojars.bru/plist-clj.svg)](https://clojars.org/net.clojars.bru/plist-clj)
[![License](https://img.shields.io/badge/License-EPL%201.0-blue.svg)](LICENSE)

> ![NOTE]
> This library has been extracted from a project and at present time only aims at supporting the features required to said project
> rather than the full Property List spec. More features will come later as required or as time and energy allow.

## Overview

A pure ClojureScript (WIP) implementation of Apple's binary property list format. XML and JSON plists are not supported (yet).

**Platform Support:**
- ✅ ClojureScript (Browser)
- ✅ ClojureScript (Node.js)
- 🚧 Clojure (JVM) - Planned for v0.2.0

## Installation

**Clojure CLI (deps.edn):**
```clojure
{:deps {net.clojars.bru/plist-clj {:mvn/version "0.1.0"}}}
```

**Leiningen (project.clj):**
```clojure
[net.clojars.bru/plist-clj "0.1.0"]
```

## Quick Start

```clojure
(require '[plist.core :as plist])

;; Encode data
(def data {"name" "John" "age" 30 "active" true})
(def encoded (plist/as-binary data))
; => "bplist00..." (binary string)

;; Decode data
(plist/parse encoded)
; => {"name" "John" "age" 30 "active" true}
```

## Features

### Supported Data Types

- **Primitives**: `nil`, booleans
- **Numbers**: Integers (up to JavaScript's `MAX_SAFE_INTEGER`)
- **Strings**: ASCII strings
- **Maps**: Nested dictionaries (maps)

### Not Yet Supported

- Arrays/vectors
- Sets
- Dates
- Floating-point numbers
- Unicode strings (non-ASCII)

## API Documentation

### `as-binary`

```clojure
(as-binary data) => binary-string
```

Encodes a ClojureScript data structure into binary plist format.

**Parameters:**
- `data` - Data structure to encode (map, string, integer, boolean, or nil)

**Returns:** Binary string representation

**Throws:** Error if data contains unsupported types

**Example:**
```clojure
(plist/as-binary {"key" "value"})
; => "bplist00\u0001\u0005..."
```

### `parse`

```clojure
(parse binary-string) => data
```

Decodes a binary plist string into a ClojureScript data structure.

**Parameters:**
- `binary-string` - Binary plist string (must start with "bplist")

**Returns:** Decoded data structure

**Throws:** Error if string is not valid binary plist format

**Example:**
```clojure
(plist/parse "bplist00\u0001\u0005...")
; => {"key" "value"}
```

## Usage Examples

### Round-Trip Serialization

```clojure
(let [original {"user" "alice" "count" 42}
      encoded (plist/as-binary original)
      decoded (plist/parse encoded)]
  (= original decoded))
; => true
```

## Testing

### Run Tests Locally

```bash
# Install dependencies
npm install

# Run browser tests (interactive with watch)
npm run test:browser:watch
# Open http://localhost:8023 in your browser

# Run Node.js tests (one-time)
npm run test:node

# Run Node.js tests in watch mode (auto-reruns on save)
npm run test:node:watch

# Run all tests (browser + node, for CI)
npm test
```

## Development

### REPL-Driven Development

This project uses shadow-cljs with Node.js REPL for interactive development.

```bash
npx shadow-cljs node-repl
```

In the REPL, manually require namespaces as needed:

```clojure
(require '[plist.core :as plist])
(require '[plist.plutil :as plutil])
(require '[plist.fixtures :as fixtures])
```

#### Available Dev Namespaces

- **`plist.plutil`** (macOS only): Validate plists with Apple's plutil
- **`plist.fixtures`** (macOS only): Generate test fixtures
- **`gen`**: Bulk fixture generation script

See [dev/README.md](dev/README.md) for complete documentation.

### Integration Testing with plutil

This project includes integration tests using fixture files validated by macOS's `plutil`.

```bash
npm run test:node  # Includes fixture tests
```

All fixtures are pre-generated and committed, so tests work on all platforms.

#### Generating Fixtures (macOS Only)

**Interactive (recommended)**:

```bash
npx shadow-cljs node-repl
```

```clojure
(require '[plist.plutil :as plutil])
(require '[plist.fixtures :as fixtures])

(plutil/test-encode-compatibility {"foo" "bar"})
(fixtures/generate-all-fixtures!)
```

**Quick script**:

```bash
npm run gen-fixtures
```

See [dev/README.md](dev/README.md) for complete documentation.

## Implementation Details

### Binary Plist Format

Apple's binary plist format consists of:

1. **Header** (8 bytes): "bplist00"
2. **Object Table**: Serialized objects with type markers
3. **Offset Table**: Byte offsets for each object
4. **Trailer** (32 bytes): Metadata (object count, root offset, etc.)

### Type Encoding

Each object has a 1-byte marker: `[type_nibble][info_nibble]`

- Type codes: 0=primitive, 1=int, 5=ascii, 13=dict
- Variable length values use continuation bytes
- Supports value deduplication for efficiency

## Roadmap (tentative)

### v0.1.0 (Current)
- ✅ Binary plist encoding and decoding
- ✅ Support for nil, booleans, integers, ASCII strings, maps
- ✅ ClojureScript support (browser and Node.js)
- ✅ Comprehensive test suite

### v0.2.0 (Planned)
- 🚧 Cross-platform support (Clojure/JVM)
- 🚧 Array/vector support
- 🚧 Unicode string support

### Future Versions
- Date support
- Float/real number support
- Set support
- Performance optimizations
- Streaming API for large plists

## Related Projects

- **[cljs-vector-clock](https://github.com/bru/cljs-vector-clock)** - Vector clock implementation using this library for serialization

## License

Copyright © 2025 bru

Distributed under the Eclipse Public License version 1.0.

## References

- [CFBinaryPList Documentation](https://developer.apple.com/documentation/corefoundation/cfpropertylist)
- [Binary Property List Format (Wikipedia)](https://en.wikipedia.org/wiki/Property_list#Binary_format)
