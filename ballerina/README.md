## Overview

The `ballerina/jsonschema` library generates [JSON Schema (draft 2020-12)](https://json-schema.org/draft/2020-12) documents from Ballerina types.

```ballerina
import ballerina/io;
import ballerina/jsonschema;

# A customer of the store.
#
# + email - Primary contact email
type Customer record {|
    # Full name of the customer
    string name;
    string email;
    int age?;
|};

public function main() returns error? {
    map<json> schema = check jsonschema:generate(Customer);
    io:println(schema.toJsonString());

    // Pretty printed JSON string
    io:println(check jsonschema:generateString(Customer));
}
```

Output:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "description": "A customer of the store.",
  "type": "object",
  "properties": {
    "name": {"description": "Full name of the customer", "type": "string"},
    "email": {"description": "Primary contact email", "type": "string"},
    "age": {"type": "integer"}
  },
  "required": ["name", "email"],
  "additionalProperties": false
}
```

## Functions

| Function | Description |
|---|---|
| `generate(typedesc<anydata> t, *Options options) returns map<json>\|Error` | Generates the schema as a `map<json>` |
| `generateString(typedesc<anydata> t, boolean pretty = true, *Options options) returns string\|Error` | Generates the schema as a JSON string |

`Options`:

| Field | Default | Description |
|---|---|---|
| `includeSchemaUri` | `true` | Emit the `$schema` keyword |
| `inlineRefs` | `false` | Inline named types instead of emitting them under `$defs` (recursive types always use `$defs`) |

## Type mapping

| Ballerina | JSON Schema |
|---|---|
| `int` | `{"type": "integer"}` |
| `byte`, `int:Signed8/16/32`, `int:Unsigned8/16/32` | `integer` with `minimum`/`maximum` |
| `float`, `decimal` | `{"type": "number"}` |
| `string` | `{"type": "string"}` |
| `string:Char` | `string` with `minLength`/`maxLength` 1 |
| `boolean` | `{"type": "boolean"}` |
| `()` | `{"type": "null"}` |
| `json`, `anydata` | `{}` |
| `T[]` / `T[n]` | `{"type": "array", "items": T}` (+ `minItems`/`maxItems` n) |
| `[A, B, C...]` | `{"type": "array", "prefixItems": [A, B], "items": C}` (`"items": false` without a rest type) |
| `map<T>` | `{"type": "object", "additionalProperties": T}` (`map<never>`: `false`) |
| `record {| ... |}` | `object` with `properties`, `required` and `"additionalProperties": false` |
| `record { ... }` | `object` with `properties` and `required` (extra properties allowed) |
| `record {| T...; |}` | `object` with `"additionalProperties": T` |
| `table<R>` | `{"type": "array", "items": R}` |
| `T?`, `A\|B` | `anyOf` |
| `enum`, `"a"\|"b"` | `{"enum": [...]}` |
| singleton (`"a"`) | `{"const": "a"}` |
| `readonly & T` | schema of `T` |
| named types | `$defs` + `$ref` (recursion supported; references to the root type use `"$ref": "#"`) |

A field is listed in `required` unless it is optional (`T x?`) or has a default value. Default values become the
`default` keyword (see [Default values](#default-values)).
`readonly` fields get `"readOnly": true`.

Unions are simplified: identical members are listed once (`float|decimal` is just `{"type": "number"}`) and a union
with a member that accepts any value (`json|int`) is `{}`.

`xml` types can't be represented and result in a `jsonschema:Error` that says where the type is, e.g.
`unsupported type for JSON Schema generation: 'xml' at '$.inner.docs[*]'`.

## Doc comments

Doc comments on type definitions and record fields become `description` keywords. Both styles of field documentation are supported:

```ballerina
# A user.
#
# + email - Contact email           // `+ field - ...` lines on the type
type User record {|
    # Display name                  // doc comment on the field
    string name;
    string email;
|};
```

Doc comments are not available at runtime, so the library ships a compiler plugin that copies them into
`@jsonschema:Description` annotations at compile time. This happens in every module that imports `ballerina/jsonschema`.

A module that only declares types (and doesn't call any `jsonschema` function) can import the library with the `_`
prefix just to get its doc comments converted:

```ballerina
import ballerina/jsonschema as _;

# A circle.
public type Circle record {|
    # Radius in cm
    float radius;
|};
```

Types defined in a module that doesn't import `ballerina/jsonschema` at all have no descriptions. Use
`@jsonschema:Meta {description: "..."}` explicitly for those, or add the import above.

When types from different modules share a name, the later ones get the module name as a prefix in `$defs`. For
example, `Address` and `mypackage_models_Address` for the `mypackage.models` module.

`# + field - ...` lines apply to the fields of every top-level record of the type definition (e.g. each member of a
union of records), but not to records nested inside field types.

Known limitations (the Ballerina runtime doesn't keep these annotations):

- Doc comments (and annotations) of `enum` declarations are not available. Use a union of string literals
  (`type Light "RED"|"GREEN";`) to get a description.
- Fields included with `*T` don't carry their doc comments (or other annotations) into the including record.

## Default values

The compiler plugin copies the default value of a record field into a `@jsonschema:Default` annotation, which becomes
the `default` keyword. This is only done when evaluating the default value has no side effects:

| Default value expression | `default` |
|---|---|
| Literals: `1`, `-1.5`, `"a"`, `true`, `()`, `null` | ✓ |
| Constants and enum members: `MAX`, `RED`, `mod:MAX` | ✓ |
| Module-level `final` and `configurable` variables: `defaultRegion`, `mod:x` | ✓ (the value at run time; for a `configurable`, the configured value) |
| List and mapping constructors of the above: `[1, MAX]`, `{host: "localhost"}` | ✓ |
| Function and method calls, operators (`MAX * 2`), templates, casts, spreads, local variables | ✗ |

```ballerina
const int MAX_RETRIES = 3;
configurable int timeout = 30;

type Settings record {|
    int retries = MAX_RETRIES;   // "default": 3
    int timeout = timeout;       // "default": 30 (or the value in Config.toml)
    string id = uuid:createType4AsString();  // no "default"
|};
```

Generating a schema never calls a default value function. For other default values, write the annotation by hand:
`@jsonschema:Default {value: "pending"}`.

When a default value is a mapping constructor of a record type (e.g. `Server server = {host: "localhost"};`), the
`default` also includes the defaults of the record's fields (e.g. `"port": 8080`), as long as those have a `default`
too. Default values that can't be represented in JSON (`xml`, `float:NaN`, ...) are left out.

## Types from other packages

The schema of a type from another package, including packages published to Ballerina Central, is generated from the
type itself, so the structure, `required`, constraints and `@jsonschema:Meta` annotations are always included.

Descriptions and defaults are added by the compiler plugin when the package that **defines** the type is compiled. A
package that imports `ballerina/jsonschema` (e.g. with `import ballerina/jsonschema as _;`) is published with them.
Packages that don't import it have no `description` or `default` keywords in their schemas.

## Annotations

### `@jsonschema:Meta`

Adds `title`, `description`, `examples`, `format` and `deprecated` to a type or a record field.

- `Meta.description` takes precedence over the doc comment. Fields left out of `Meta` are not emitted, so
  `@jsonschema:Meta {}` adds nothing.
- On a field of a named type, the field's keywords go next to the `$ref`, and the type's own `Meta` stays in
  `$defs`. With `inlineRefs = true`, the field's keywords override the type's, and any keyword the field doesn't
  set falls back to the type's value.
- `examples` and `format` are copied as is. They aren't checked against the type.

```ballerina
@jsonschema:Meta {title: "User", examples: [{"email": "alice@example.com"}]}
type User record {|
    @jsonschema:Meta {format: "email"}
    string email;
|};
```

### `ballerina/constraint` annotations

| Annotation | JSON Schema |
|---|---|
| `@constraint:String {length, minLength, maxLength, pattern}` | `minLength`, `maxLength`, `pattern` |
| `@constraint:Int/Float/Number {minValue, maxValue, minValueExclusive, maxValueExclusive}` | `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum` |
| `@constraint:Array {length, minLength, maxLength}` | `minItems`, `maxItems` |

`constraint:validate` checks the constraints of both a field and its type. With `inlineRefs = true`, both are merged
into one schema: the stricter `minimum`/`maximum`/length bound is kept, and a second `pattern` goes into `allOf`. The
range of `byte` and `int:Signed*`/`Unsigned*` types is merged the same way.
