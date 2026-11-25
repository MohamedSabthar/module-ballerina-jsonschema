// Copyright (c) 2026 WSO2 LLC (http://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/constraint;
import ballerina/test;

// ------------------------------------------------------------------------------------------------ records

type EmptyClosed record {||};

type EmptyOpen record {};

type OnlyRest record {|
    string...;
|};

type JsonRest record {|
    int id;
    json...;
|};

type NeverRest record {|
    int id;
    never...;
|};

type NullableOptional record {|
    int? a?;
    int? b;
    int c?;
|};

type EscapedNames record {|
    string 'type;
    string first\-name;
    string 'string\ value;
|};

type BaseRecord record {|
    string id;
    int version = 1;
|};

type IncludingRecord record {|
    *BaseRecord;
    string name;
|};

type OverridingRecord record {|
    *BaseRecord;
    string:Char id;
|};

type ReadonlyFields record {|
    readonly string id;
    readonly int[] values;
    string mutable;
|};

type ReadonlyRecord readonly & record {|
    string id;
|};

type DeeplyNested record {|
    record {|
        record {|
            record {|
                int leaf;
            |} level3;
        |} level2;
    |} level1;
|};

@test:Config
function testEmptyRecords() returns error? {
    test:assertEquals(check generate(EmptyClosed, includeSchemaUri = false),
            {"type": "object", "properties": {}, "additionalProperties": false});
    test:assertEquals(check generate(EmptyOpen, includeSchemaUri = false), {"type": "object", "properties": {}});
}

@test:Config
function testRestFields() returns error? {
    test:assertEquals(check generate(OnlyRest, includeSchemaUri = false),
            {"type": "object", "properties": {}, "additionalProperties": {"type": "string"}});
    test:assertEquals(check generate(JsonRest, includeSchemaUri = false),
            {"type": "object", "properties": {"id": {"type": "integer"}}, "required": ["id"]});
    test:assertEquals(check generate(NeverRest, includeSchemaUri = false), {
        "type": "object",
        "properties": {"id": {"type": "integer"}},
        "required": ["id"],
        "additionalProperties": false
    });
}

@test:Config
function testNullableAndOptionalFields() returns error? {
    test:assertEquals(check generate(NullableOptional, includeSchemaUri = false), {
        "type": "object",
        "properties": {
            "a": {"anyOf": [{"type": "integer"}, {"type": "null"}]},
            "b": {"anyOf": [{"type": "integer"}, {"type": "null"}]},
            "c": {"type": "integer"}
        },
        "required": ["b"],
        "additionalProperties": false
    });
}

@test:Config
function testEscapedFieldNames() returns error? {
    map<json> schema = check generate(EscapedNames, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties.keys(), ["type", "first-name", "string value"]);
    test:assertEquals(schema["required"], ["type", "first-name", "string value"]);
}

@test:Config
function testRecordInclusion() returns error? {
    // Included fields come after the record's own fields.
    test:assertEquals(check generate(IncludingRecord, includeSchemaUri = false), {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "id": {"type": "string"},
            "version": {"type": "integer"}
        },
        "required": ["name", "id"],
        "additionalProperties": false
    });
    map<json> overriding = check generate(OverridingRecord, includeSchemaUri = false);
    map<json> properties = check overriding.properties.ensureType();
    test:assertEquals(properties["id"], {"type": "string", "minLength": 1, "maxLength": 1});
}

@test:Config
function testReadonly() returns error? {
    map<json> schema = check generate(ReadonlyFields, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["id"], {"type": "string", "readOnly": true});
    test:assertEquals(properties["values"], {"type": "array", "items": {"type": "integer"}, "readOnly": true});
    test:assertEquals(properties["mutable"], {"type": "string"});

    // `readonly &` makes the value immutable but doesn't declare `readonly` fields.
    test:assertEquals(check generate(ReadonlyRecord, includeSchemaUri = false), {
        "type": "object",
        "properties": {"id": {"type": "string"}},
        "required": ["id"],
        "additionalProperties": false
    });
}

@test:Config
function testDeeplyNestedAnonymousRecords() returns error? {
    map<json> schema = check generate(DeeplyNested, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("$defs"));
    json leaf = check schema.properties.level1.properties.level2.properties.level3.properties.leaf;
    test:assertEquals(leaf, {"type": "integer"});
}

// ------------------------------------------------------------------------------------------------ collections

type IntMatrix int[][];

type OptionalInts (int?)[];

type EmptyArray int[0];

type Bytes byte[];

type NestedMap map<map<int>>;

type AnydataMap map<anydata>;

type TupleWithRecordRest [string, Address...];

type ArrayOfTuples [int, string][];

type AddressTable table<Address>;

type AnyJsonArray json[];

@test:Config
function testArrays() returns error? {
    test:assertEquals(check generate(IntMatrix, includeSchemaUri = false),
            {"type": "array", "items": {"type": "array", "items": {"type": "integer"}}});
    test:assertEquals(check generate(OptionalInts, includeSchemaUri = false),
            {"type": "array", "items": {"anyOf": [{"type": "integer"}, {"type": "null"}]}});
    test:assertEquals(check generate(EmptyArray, includeSchemaUri = false),
            {"type": "array", "items": {"type": "integer"}, "minItems": 0, "maxItems": 0});
    test:assertEquals(check generate(Bytes, includeSchemaUri = false),
            {"type": "array", "items": {"type": "integer", "minimum": 0, "maximum": 255}});
    test:assertEquals(check generate(AnyJsonArray, includeSchemaUri = false), {"type": "array", "items": {}});
}

@test:Config
function testMaps() returns error? {
    test:assertEquals(check generate(NestedMap, includeSchemaUri = false), {
        "type": "object",
        "additionalProperties": {"type": "object", "additionalProperties": {"type": "integer"}}
    });
    test:assertEquals(check generate(AnydataMap, includeSchemaUri = false), {"type": "object"});
}

@test:Config
function testTuples() returns error? {
    test:assertEquals(check generate(TupleWithRecordRest, includeSchemaUri = false), {
        "type": "array",
        "prefixItems": [{"type": "string"}],
        "items": {"$ref": "#/$defs/Address"},
        "minItems": 1,
        "$defs": {"Address": check addressSchema()}
    });
    test:assertEquals(check generate(ArrayOfTuples, includeSchemaUri = false), {
        "type": "array",
        "items": {
            "type": "array",
            "prefixItems": [{"type": "integer"}, {"type": "string"}],
            "items": false,
            "minItems": 2
        }
    });
}

@test:Config
function testTableWithoutKey() returns error? {
    test:assertEquals(check generate(AddressTable, includeSchemaUri = false), {
        "type": "array",
        "items": {"$ref": "#/$defs/Address"},
        "$defs": {"Address": check addressSchema()}
    });
}

// ------------------------------------------------------------------------------------------------ unions, literals

enum Weekday {
    MON = "monday",
    TUE = "tuesday"
}

type MixedLiterals 1|"one"|true|1.5;

type DecimalLiteral 2.5d;

type NilOnlyUnion ()|();

type RecordUnion Address|Person;

type UnionWithJson int|json;

type NestedUnion (int|string)|boolean;

type LiteralsAndType "a"|"b"|int;

type UnionOfNamedAndAnonymous Address|record {| int x; |};

@test:Config
function testEnumWithValues() returns error? {
    map<json> schema = check generate(Weekday, includeSchemaUri = false);
    assertEnum(schema, ["monday", "tuesday"]);
}

@test:Config
function testMixedLiterals() returns error? {
    map<json> schema = check generate(MixedLiterals, includeSchemaUri = false);
    assertEnum(schema, [1, "one", true, 1.5d]);
    test:assertEquals(check generate(DecimalLiteral, includeSchemaUri = false), {"const": 2.5d});
}

@test:Config
function testNilUnion() returns error? {
    test:assertEquals(check generate(NilOnlyUnion, includeSchemaUri = false), {"type": "null"});
}

@test:Config
function testRecordUnion() returns error? {
    map<json> schema = check generate(RecordUnion, includeSchemaUri = false);
    test:assertEquals(schema["anyOf"], [{"$ref": "#/$defs/Address"}, {"$ref": "#/$defs/Person"}]);
    map<json> defs = check schema["$defs"].ensureType();
    test:assertEquals(defs.keys().sort(), ["Address", "Person"]);
}

@test:Config
function testUnionWithJson() returns error? {
    // `json` accepts any value, so the union does too.
    test:assertEquals(check generate(UnionWithJson, includeSchemaUri = false), {});
}

@test:Config
function testNestedUnionIsFlattened() returns error? {
    map<json> schema = check generate(NestedUnion, includeSchemaUri = false);
    json[] anyOf = check schema["anyOf"].ensureType();
    test:assertEquals(anyOf.length(), 3, schema.toJsonString());
    foreach json member in [{"type": "integer"}, {"type": "string"}, {"type": "boolean"}] {
        test:assertTrue(anyOf.indexOf(member) != (), schema.toJsonString());
    }
}

@test:Config
function testLiteralsMixedWithTypes() returns error? {
    map<json> schema = check generate(LiteralsAndType, includeSchemaUri = false);
    json[] anyOf = check schema["anyOf"].ensureType();
    test:assertTrue(anyOf.indexOf({"type": "integer"}) != (), schema.toJsonString());
    string serialized = schema.toJsonString();
    test:assertTrue(serialized.includes("\"a\"") && serialized.includes("\"b\""), serialized);
}

@test:Config
function testUnionOfNamedAndAnonymousRecords() returns error? {
    map<json> schema = check generate(UnionOfNamedAndAnonymous, includeSchemaUri = false);
    test:assertEquals(schema["anyOf"], [
        {"$ref": "#/$defs/Address"},
        {
            "type": "object",
            "properties": {"x": {"type": "integer"}},
            "required": ["x"],
            "additionalProperties": false
        }
    ]);
}

// ------------------------------------------------------------------------------------------------ named types, recursion

type Age int;

type AgeAlias Age;

type AgeAliasAlias AgeAlias;

type WithAliases record {|
    Age age;
    AgeAliasAlias otherAge;
|};

type Employee record {|
    string name;
    Department department;
|};

type Department record {|
    string name;
    Employee[] employees;
|};

type RecursiveMap record {|
    map<RecursiveMap> children;
|};

type RecursiveOptional record {|
    RecursiveOptional? parent;
|};

type ReusedTwice record {|
    Address home;
    Address work;
    Address[] others;
    map<Address> byName;
|};

@test:Config
function testAliasChains() returns error? {
    test:assertEquals(check generate(Age, includeSchemaUri = false), {"type": "integer"});
    test:assertEquals(check generate(AgeAliasAlias, includeSchemaUri = false), {
        "$ref": "#/$defs/AgeAlias",
        "$defs": {"Age": {"type": "integer"}, "AgeAlias": {"$ref": "#/$defs/Age"}}
    });
    map<json> schema = check generate(WithAliases, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(schema["properties"], {"age": {"type": "integer"}, "otherAge": {"type": "integer"}});
}

@test:Config
function testMutualRecursion() returns error? {
    test:assertEquals(check generate(Employee, includeSchemaUri = false), {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "department": {"$ref": "#/$defs/Department"}
        },
        "required": ["name", "department"],
        "additionalProperties": false,
        "$defs": {
            "Department": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "employees": {"type": "array", "items": {"$ref": "#"}}
                },
                "required": ["name", "employees"],
                "additionalProperties": false
            }
        }
    });
}

@test:Config
function testMutualRecursionInlined() returns error? {
    // The root is referred to with "#"; nothing else is recursive, so nothing needs `$defs`.
    test:assertEquals(check generate(Employee, includeSchemaUri = false, inlineRefs = true), {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "department": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "employees": {"type": "array", "items": {"$ref": "#"}}
                },
                "required": ["name", "employees"],
                "additionalProperties": false
            }
        },
        "required": ["name", "department"],
        "additionalProperties": false
    });
}

@test:Config
function testRecursionThroughMapAndOptional() returns error? {
    test:assertEquals(check generate(RecursiveMap, includeSchemaUri = false), {
        "type": "object",
        "properties": {"children": {"type": "object", "additionalProperties": {"$ref": "#"}}},
        "required": ["children"],
        "additionalProperties": false
    });
    test:assertEquals(check generate(RecursiveOptional, includeSchemaUri = false), {
        "type": "object",
        "properties": {"parent": {"anyOf": [{"$ref": "#"}, {"type": "null"}]}},
        "required": ["parent"],
        "additionalProperties": false
    });
}

@test:Config
function testSameTypeReferencedManyTimes() returns error? {
    map<json> schema = check generate(ReusedTwice, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {
        "home": {"$ref": "#/$defs/Address"},
        "work": {"$ref": "#/$defs/Address"},
        "others": {"type": "array", "items": {"$ref": "#/$defs/Address"}},
        "byName": {"type": "object", "additionalProperties": {"$ref": "#/$defs/Address"}}
    });
    map<json> defs = check schema["$defs"].ensureType();
    test:assertEquals(defs.keys(), ["Address"]);
}

@test:Config
function testKeywordOrder() returns error? {
    string schema = check generateString(Person, false);
    test:assertTrue(schema.startsWith(string `{"$schema":"${SCHEMA_DIALECT}","type":"object",`), schema);
    test:assertTrue(schema.includes(string `"additionalProperties":false,"$defs":{"Address":`), schema);
}

// ------------------------------------------------------------------------------------------------ annotations

@Meta {description: "An age in years", examples: [30]}
@constraint:Int {minValue: 0}
type AnnotatedAge int;

type FieldOverridesAlias record {|
    @constraint:Int {maxValue: 150}
    @Meta {description: "Age of the person"}
    AnnotatedAge age;
|};

@Meta {title: "Inline me"}
type InlineTarget record {|
    int x;
|};

type HasInlineTarget record {|
    InlineTarget target;
|};

@Description {value: "Weekday enum"}
type DescribedStatus "on"|"off";

type ConstrainedArrayItems record {|
    @constraint:Array {length: 2}
    @Meta {description: "Exactly two"}
    string[] pair;
|};

type UnicodeMeta record {|
    @Meta {description: "Prix en €, 日本語, emoji 😀", examples: ["ünïcödé"]}
    string price;
|};

@test:Config
function testAnnotationsOnAliasAndField() returns error? {
    map<json> schema = check generate(FieldOverridesAlias, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {
        "age": {"description": "Age of the person", "$ref": "#/$defs/AnnotatedAge", "maximum": 150}
    });
    test:assertEquals(schema["$defs"], {
        "AnnotatedAge": {"description": "An age in years", "type": "integer", "minimum": 0, "examples": [30]}
    });
}

@test:Config
function testAnnotationsKeptWhenInlined() returns error? {
    map<json> schema = check generate(HasInlineTarget, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(schema["properties"], {
        "target": {
            "title": "Inline me",
            "type": "object",
            "properties": {"x": {"type": "integer"}},
            "required": ["x"],
            "additionalProperties": false
        }
    });
}

@test:Config
function testDescriptionOnLiteralUnion() returns error? {
    test:assertEquals(check generate(DescribedStatus, includeSchemaUri = false),
            {"description": "Weekday enum", "enum": ["on", "off"]});
}

@test:Config
function testMultipleConstraintsOnOneField() returns error? {
    map<json> schema = check generate(ConstrainedArrayItems, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {
        "pair": {"description": "Exactly two", "type": "array", "items": {"type": "string"}, "minItems": 2, "maxItems": 2}
    });
}

@test:Config
function testUnicode() returns error? {
    map<json> schema = check generate(UnicodeMeta, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {
        "price": {"description": "Prix en €, 日本語, emoji 😀", "type": "string", "examples": ["ünïcödé"]}
    });
}

// ------------------------------------------------------------------------------------------------ errors and options

type NestedXml record {|
    record {|
        map<xml> docs;
    |} inner;
|};

type XmlInUnion int|xml;

type XmlTable table<record {| xml x; |}>;

type JustXml xml;

type CharAlias string:Char;

type JsonMap map<json>;

@test:Config
function testUnsupportedTypesNested() {
    typedesc<anydata>[] types = [NestedXml, XmlInUnion, XmlTable, JustXml];
    foreach typedesc<anydata> t in types {
        map<json>|Error schema = generate(t);
        if schema is map<json> {
            test:assertFail(string `expected an error for ${t.toString()}`);
        }
        test:assertTrue(schema.message().startsWith("unsupported type for JSON Schema generation: "),
                schema.message());
    }
    string|Error schemaString = generateString(NestedXml);
    test:assertTrue(schemaString is Error);
}

@test:Config
function testBuiltinRootTypes() returns error? {
    test:assertEquals(check generate(anydata, includeSchemaUri = false), {});
    test:assertEquals(check generate(byte, includeSchemaUri = false), {"type": "integer", "minimum": 0, "maximum": 255});
    test:assertEquals(check generate(CharAlias, includeSchemaUri = false),
            {"type": "string", "minLength": 1, "maxLength": 1});
    test:assertEquals(check generate(JsonMap, includeSchemaUri = false), {"type": "object"});
}

@test:Config
function testSchemaUriWithDefs() returns error? {
    map<json> schema = check generate(Person);
    test:assertEquals(schema.keys(), ["$schema", "type", "properties", "required", "additionalProperties", "$defs"]);
}

@test:Config
function testRepeatedGenerationIsStable() returns error? {
    string first = check generateString(Department);
    string second = check generateString(Department);
    test:assertEquals(first, second);
    // Generating another type in between doesn't leak state.
    _ = check generate(Employee);
    test:assertEquals(check generateString(Department), first);
}

@test:Config
function testGeneratedSchemaIsValidJson() returns error? {
    foreach typedesc<anydata> t in [Person, Pixel, Constrained, Examples, Department, JsonLike] {
        string text = check generateString(t);
        json parsed = check text.fromJsonString();
        test:assertTrue(parsed is map<json>);
    }
}

function addressSchema() returns map<json>|error => {
    "type": "object",
    "properties": {
        "street": {"type": "string"},
        "city": {"type": "string"},
        "zip": {"type": "string"}
    },
    "required": ["street", "city"],
    "additionalProperties": false
};

@Meta {title: "Location", description: "Type-level description", examples: [{"city": "Colombo"}]}
type Location record {|
    string city;
|};

type HasDescribedLocations record {|
    @Meta {title: "Home", description: "Field-level description", examples: [{"city": "Kandy"}]}
    Location home;
    @Meta {title: "Work"}
    Location work;
    Location other;
|};

@test:Config
function testFieldMetaWinsOverInlinedTypeMeta() returns error? {
    map<json> schema = check generate(HasDescribedLocations, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(schema["properties"], {
        "home": inlinedLocation("Home", "Field-level description", "Kandy"),
        // Keywords not given on the field fall back to the type's.
        "work": inlinedLocation("Work", "Type-level description", "Colombo"),
        "other": inlinedLocation("Location", "Type-level description", "Colombo")
    });

    // With $defs, the field keywords sit next to the $ref and the type's stay in $defs.
    schema = check generate(HasDescribedLocations, includeSchemaUri = false);
    test:assertEquals(check schema.properties.home, {"title": "Home", "description": "Field-level description",
        "$ref": "#/$defs/Location", "examples": [{"city": "Kandy"}]});
    test:assertEquals(check schema.properties.work, {"title": "Work", "$ref": "#/$defs/Location"});
}

function inlinedLocation(string title, string description, string exampleCity) returns json => {
    "title": title,
    "description": description,
    "type": "object",
    "properties": {"city": {"type": "string"}},
    "required": ["city"],
    "additionalProperties": false,
    "examples": [{"city": exampleCity}]
};

type XmlTupleMember [int, xml];

type XmlTupleRest [int, xml...];

type XmlRest record {| int a; xml...; |};

type XmlElementField record {| xml:Element element; |};

@test:Config
function testUnsupportedTypeErrorPaths() {
    map<typedesc<anydata>> cases = {
        "'xml'": JustXml,
        "'xml' at '$.inner.docs.*'": NestedXml,
        "'xml'  (union)": XmlInUnion,
        "'xml' at '$[*].x'": XmlTable,
        "'xml' at '$[1]'": XmlTupleMember,
        "'xml' at '$[*]'": XmlTupleRest,
        "'xml' at '$.*'": XmlRest,
        "'xml:Element' at '$.element'": XmlElementField
    };
    foreach [string, typedesc<anydata>] [expected, t] in cases.entries() {
        map<json>|Error schema = generate(t);
        string suffix = expected.endsWith("(union)") ? "'xml'" : expected;
        test:assertEquals(schema is Error ? schema.message() : "no error",
                "unsupported type for JSON Schema generation: " + suffix, t.toString());
    }
}

type FloatOrDecimal float|decimal;

type IntFloatDecimal int|float|decimal;

type JsonOrInt json|int;

type NestedAnyOrString (anydata|int)|string;

type NeverValues map<never>;

@test:Config
function testUnionSimplification() returns error? {
    // Identical members are listed once; a single remaining member is used as is.
    test:assertEquals(check generate(FloatOrDecimal, includeSchemaUri = false), {"type": "number"});
    test:assertEquals(check generate(IntFloatDecimal, includeSchemaUri = false),
            {"anyOf": [{"type": "integer"}, {"type": "number"}]});
    // A member that accepts anything makes the whole union accept anything.
    test:assertEquals(check generate(JsonOrInt, includeSchemaUri = false), {});
    test:assertEquals(check generate(NestedAnyOrString, includeSchemaUri = false), {});
    test:assertEquals(check generate(NeverValues, includeSchemaUri = false),
            {"type": "object", "additionalProperties": false});
}
