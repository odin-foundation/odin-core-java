# odin-core

[![Maven Central](https://img.shields.io/maven-central/v/foundation.odin/odin-core)](https://central.sonatype.com/artifact/foundation.odin/odin-core) [![License](https://img.shields.io/badge/license-Apache--2.0-blue)](https://github.com/odin-foundation/odin-core-java/blob/main/LICENSE)

Official Java SDK for [ODIN](https://odin.foundation) (Open Data Interchange Notation) — a canonical data model for transporting meaning between systems, standards, and AI.

## Install

**Maven:**

```xml
<dependency>
    <groupId>foundation.odin</groupId>
    <artifactId>odin-core</artifactId>
    <version>1.0</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'foundation.odin:odin-core:1.0'
```

**Requires Java 21+**

## Quick Start

```java
import foundation.odin.Odin;

var doc = Odin.parse("""
    {policy}
    number = "PAP-2024-001"
    effective = 2024-06-01
    premium = #$747.50
    active = ?true
    """);

System.out.println(doc.get("policy.number")); // "PAP-2024-001"
System.out.println(doc.get("policy.premium")); // 747.50

String text = Odin.stringify(doc);
```

## Core API

| Method | Description | Example |
|--------|-------------|---------|
| `Odin.parse(text)` | Parse ODIN text into a document | `var doc = Odin.parse(src);` |
| `Odin.stringify(doc)` | Serialize document to ODIN text | `var text = Odin.stringify(doc);` |
| `Odin.canonicalize(doc)` | Deterministic bytes for hashing/signatures | `byte[] bytes = Odin.canonicalize(doc);` |
| `Odin.validate(doc, schema)` | Validate against an ODIN schema | `var result = Odin.validate(doc, schema);` |
| `Odin.parseSchema(text)` | Parse a schema definition | `var schema = Odin.parseSchema(src);` |
| `Odin.diff(a, b)` | Structured diff between two documents | `var changes = Odin.diff(docA, docB);` |
| `Odin.patch(doc, diff)` | Apply a diff to a document | `var updated = Odin.patch(doc, changes);` |
| `Odin.parseTransform(text)` | Parse a transform specification | `var tx = Odin.parseTransform(src);` |
| `Odin.executeTransform(tx, source)` | Run a transform on data | `var out = Odin.executeTransform(tx, doc);` |
| `Odin.path(segments)` | Build a path for nested access | `var p = Odin.path("policy", "number");` |
| `doc.toJson()` | Export to JSON | `String json = doc.toJson();` |
| `doc.toXml()` | Export to XML | `String xml = doc.toXml();` |
| `doc.toCsv()` | Export to CSV | `String csv = doc.toCsv();` |
| `Odin.serialize(doc)` | Export to ODIN | `String odin = Odin.serialize(doc);` |
| `Odin.builder()` | Fluent document builder | `Odin.builder().section("policy")...` |

## Schema Validation

```java
import foundation.odin.Odin;

var schema = Odin.parseSchema("""
    {policy}
    !number : string
    !effective : date
    !premium : currency
    active : boolean
    """);

var doc = Odin.parse(source);
var result = Odin.validate(doc, schema);

if (!result.isValid()) {
    result.errors().forEach(System.err::println);
}
```

## Transforms

```java
import foundation.odin.Odin;

var transform = Odin.parseTransform("""
    map policy -> record
      policy.number -> record.id
      policy.premium -> record.amount
    """);

var result = Odin.executeTransform(transform, doc);
```

## Export

```java
String odin = Odin.serialize(doc); // ODIN string
String json = doc.toJson();       // JSON string
String xml  = doc.toXml();        // XML string
String csv  = doc.toCsv();        // CSV string
```

## Builder

```java
var doc = Odin.builder()
    .section("policy")
    .set("number", "PAP-2024-001")
    .set("effective", LocalDate.of(2024, 6, 1))
    .set("premium", OdinCurrency.of(747.50))
    .set("active", true)
    .build();
```

## Path Builder

```java
var path = Odin.path("policy", "coverage", "limit");
var value = doc.get(path);
```

## Testing

Tests use [JUnit 5](https://junit.org/junit5/) and the shared golden test suite:

```bash
mvn test
```

## Links

- [.Odin Foundation Website](https://odin.foundation)
- [GitHub](https://github.com/odin-foundation/odin)
- [Golden Test Suite](https://github.com/odin-foundation/odin/tree/main/sdk/golden)
- [License (Apache 2.0)](https://github.com/odin-foundation/odin/blob/main/LICENSE)
