# Ballerina JSON Schema Library

The `ballerina/jsonschema` library generates [JSON Schema (draft 2020-12)](https://json-schema.org/draft/2020-12)
documents from Ballerina types at runtime:

```ballerina
import ballerina/constraint;
import ballerina/jsonschema;

# A customer of the store.
type Customer record {|
    # Full name of the customer
    string name;
    @constraint:Int {minValue: 0}
    int age?;
    string tier = "basic";
|};

map<json> schema = check jsonschema:generate(Customer);
```

- Records, maps, arrays, tuples, tables, unions, enums, singletons and recursive types
- Doc comments become `description`, default values become `default` (via a compiler plugin)
- `ballerina/constraint` annotations become `minLength`, `maximum`, `pattern`, ...
- `@jsonschema:Meta` for `title`, `examples`, `format` and `deprecated`

See the [module documentation](ballerina/README.md) for the full type mapping and options.

## Repository structure

| Directory | Contents |
|---|---|
| `ballerina/` | The Ballerina module (`generate`, `generateString`, annotations) and its unit tests |
| `native/` | Java implementation of the schema generator |
| `compiler-plugin/` | Compiler plugin that turns doc comments and default values into annotations |
| `compiler-plugin-tests/` | Tests of the compiler plugin |
| `ballerina-tests/` | End-to-end tests (the compiler plugin and the runtime together) |
| `build-config/` | Templates for `Ballerina.toml` / `CompilerPlugin.toml`, checkstyle and spotbugs config |

## Build from the source

Requirements: JDK 25 and a GitHub personal access token with the `read:packages` scope (the Ballerina Gradle plugin is
published to GitHub Packages).

```bash
export packageUser=<GitHub username>
export packagePAT=<GitHub token with read:packages>
./gradlew clean build
```

Useful variations:

```bash
./gradlew clean build -x test        # build without running tests
./gradlew :jsonschema-ballerina:test # run the Ballerina unit tests only
```

## License

[Apache License 2.0](LICENSE)
