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

import ballerina/test;

@test:Config
function testPrimitives() returns error? {
    test:assertEquals(check generate(int, includeSchemaUri = false), {"type": "integer"});
    test:assertEquals(check generate(string, includeSchemaUri = false), {"type": "string"});
    test:assertEquals(check generate(boolean, includeSchemaUri = false), {"type": "boolean"});
    test:assertEquals(check generate(float, includeSchemaUri = false), {"type": "number"});
    test:assertEquals(check generate(decimal, includeSchemaUri = false), {"type": "number"});
    test:assertEquals(check generate(Nil, includeSchemaUri = false), {"type": "null"});
    test:assertEquals(check generate(json, includeSchemaUri = false), {});
    test:assertEquals(check generate(OptionalInt, includeSchemaUri = false),
            {"anyOf": [{"type": "integer"}, {"type": "null"}]});
}

@test:Config
function testSchemaUri() returns error? {
    map<json> schema = check generate(string);
    test:assertEquals(schema["$schema"], SCHEMA_DIALECT);
}

@test:Config
function testRecord() returns error? {
    map<json> schema = check generate(Person, includeSchemaUri = false);
    test:assertEquals(schema, {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "age": {"type": "integer"},
            "height": {"anyOf": [{"type": "number"}, {"type": "null"}]},
            "active": {"type": "boolean"},
            "address": {"$ref": "#/$defs/Address"},
            "previousAddresses": {"type": "array", "items": {"$ref": "#/$defs/Address"}},
            "id": {"type": "string", "readOnly": true}
        },
        "required": ["name", "age", "height", "address", "id"],
        "additionalProperties": false,
        "$defs": {
            "Address": {
                "type": "object",
                "properties": {
                    "street": {"type": "string"},
                    "city": {"type": "string"},
                    "zip": {"type": "string"}
                },
                "required": ["street", "city"],
                "additionalProperties": false
            }
        }
    });
}

@test:Config
function testInlineRefs() returns error? {
    map<json> schema = check generate(Person, includeSchemaUri = false, inlineRefs = true);
    test:assertFalse(schema.hasKey("$defs"));
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["address"], {
        "type": "object",
        "properties": {
            "street": {"type": "string"},
            "city": {"type": "string"},
            "zip": {"type": "string"}
        },
        "required": ["street", "city"],
        "additionalProperties": false
    });
}

@test:Config
function testOpenRecords() returns error? {
    map<json> open = check generate(OpenRecord, includeSchemaUri = false);
    test:assertFalse(open.hasKey("additionalProperties"));
    map<json> withRest = check generate(OpenRecordWithRest, includeSchemaUri = false);
    test:assertEquals(withRest["additionalProperties"], {"type": "integer"});
}

@test:Config
function testRecursiveRoot() returns error? {
    map<json> schema = check generate(TreeNode, includeSchemaUri = false);
    test:assertEquals(schema, {
        "type": "object",
        "properties": {
            "value": {"type": "string"},
            "children": {"type": "array", "items": {"$ref": "#"}}
        },
        "required": ["value", "children"],
        "additionalProperties": false
    });
}

@test:Config
function testRecursiveNested() returns error? {
    foreach boolean inlineRefs in [false, true] {
        map<json> schema = check generate(Wrapper, includeSchemaUri = false, inlineRefs = inlineRefs);
        test:assertEquals(schema, {
            "type": "object",
            "properties": {"list": {"$ref": "#/$defs/LinkedList"}},
            "required": ["list"],
            "additionalProperties": false,
            "$defs": {
                "LinkedList": {
                    "type": "object",
                    "properties": {
                        "value": {"type": "integer"},
                        "next": {"anyOf": [{"$ref": "#/$defs/LinkedList"}, {"type": "null"}]}
                    },
                    "required": ["value", "next"],
                    "additionalProperties": false
                }
            }
        });
    }
}

@test:Config
function testEnumsUnionsTuples() returns error? {
    map<json> schema = check generate(Pixel, includeSchemaUri = false, inlineRefs = true);
    map<json> properties = check schema.properties.ensureType();
    assertEnum(properties["color"], ["RED", "GREEN", "BLUE"]);
    test:assertEquals(properties["status"], {"enum": ["active", "inactive", "pending"]});
    test:assertEquals(properties["point"], {
        "type": "array",
        "prefixItems": [{"type": "integer"}, {"type": "integer"}],
        "items": false,
        "minItems": 2
    });
    test:assertEquals(properties["path"], {
        "type": "array",
        "prefixItems": [{"type": "string"}],
        "items": {"type": "integer"},
        "minItems": 1
    });
    map<json> optionalColor = check properties["optionalColor"].ensureType();
    if optionalColor.hasKey("enum") {
        assertEnum(optionalColor, ["RED", "GREEN", "BLUE", ()]);
    } else {
        json[] anyOf = check optionalColor["anyOf"].ensureType();
        assertEnum(anyOf[0], ["RED", "GREEN", "BLUE"]);
        test:assertEquals(anyOf[1], {"type": "null"});
    }
}

@test:Config
function testConstraints() returns error? {
    map<json> schema = check generate(Constrained, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["code"], {"type": "string", "minLength": 5, "maxLength": 5});
    test:assertEquals(properties["age"], {"type": "integer", "minimum": 0, "maximum": 120});
    test:assertEquals(properties["ratio"], {"type": "number", "exclusiveMinimum": 0d, "exclusiveMaximum": 1.0d});
    test:assertEquals(properties["tags"], {"type": "array", "items": {"type": "string"}, "minItems": 1, "maxItems": 3});
    map<json> defs = check schema["$defs"].ensureType();
    test:assertEquals(defs["Username"], {"type": "string", "minLength": 2, "maxLength": 10, "pattern": "[a-z]+"});
}

@test:Config
function testMetaAndDescription() returns error? {
    map<json> schema = check generate(Product, includeSchemaUri = false);
    test:assertEquals(schema["title"], "Product");
    test:assertEquals(schema["description"], "A product in the catalog");
    test:assertEquals(schema["examples"], [{"sku": "A1"}]);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["sku"], {"description": "Stock keeping unit", "type": "string", "format": "uuid"});
    test:assertEquals(properties["legacyCode"], {"type": "string", "deprecated": true});
    test:assertEquals(properties["price"], {"description": "Price in USD", "type": "number"});
    test:assertEquals(properties["name"], {"description": "Meta wins", "type": "string"});
}

@test:Config
function testNumbersAndCollections() returns error? {
    map<json> schema = check generate(Numbers, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["b"], {"type": "integer", "minimum": 0, "maximum": 255});
    test:assertEquals(properties["s8"], {"type": "integer", "minimum": -128, "maximum": 127});
    test:assertEquals(properties["u16"], {"type": "integer", "minimum": 0, "maximum": 65535});
    test:assertEquals(properties["ch"], {"type": "string", "minLength": 1, "maxLength": 1});
    test:assertEquals(properties["fixed"], {"type": "array", "items": {"type": "integer"}, "minItems": 3, "maxItems": 3});
    test:assertEquals(properties["labels"], {"type": "object", "additionalProperties": {"type": "string"}});
    test:assertEquals(properties["extra"], {"type": "object"});
    test:assertEquals(properties["anything"], {});
}

@test:Config
function testTableAndReadonly() returns error? {
    map<json> tableSchema = check generate(ItemTable, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(tableSchema["type"], "array");
    map<json> items = check tableSchema.items.ensureType();
    test:assertEquals(items["type"], "object");

    map<json> readonlySchema = check generate(ReadOnlyPerson, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(readonlySchema["type"], "object");
    map<json> properties = check readonlySchema.properties.ensureType();
    test:assertTrue(properties.hasKey("name"));
}

@test:Config
function testUnsupportedType() {
    map<json>|Error schema = generate(WithXml);
    if schema is map<json> {
        test:assertFail("expected an error for xml");
    }
    test:assertTrue(schema.message().includes("unsupported type"));
}

@test:Config
function testGenerateString() returns error? {
    string compact = check generateString(Address, false, includeSchemaUri = false);
    test:assertEquals(compact, string `{"type":"object","properties":{"street":{"type":"string"},` +
            string `"city":{"type":"string"},"zip":{"type":"string"}},"required":["street","city"],` +
            string `"additionalProperties":false}`);
    string pretty = check generateString(Address);
    test:assertTrue(pretty.includes("\n  \"type\": \"object\""));
}

// Enum member order is not significant in JSON Schema (and Ballerina doesn't preserve the declaration order).
function assertEnum(json schema, json[] expected) {
    if schema !is map<json> || schema["enum"] !is json[] {
        test:assertFail(string `expected an enum schema, found: ${schema.toJsonString()}`);
    }
    json[] actual = <json[]>schema["enum"];
    test:assertEquals(actual.length(), expected.length(), schema.toJsonString());
    foreach json value in expected {
        test:assertTrue(actual.indexOf(value) != (), string `missing enum value ${value.toJsonString()}`);
    }
}

@test:Config
function testAnonymousRecordIsInlined() returns error? {
    map<json> schema = check generate(WithAnonymous, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("$defs"));
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["inner"], {
        "type": "object",
        "properties": {"a": {"type": "integer"}},
        "required": ["a"],
        "additionalProperties": false
    });
}

@test:Config
function testAliases() returns error? {
    map<json> expected = {
        "$ref": "#/$defs/Address",
        "$defs": {
            "Address": {
                "type": "object",
                "properties": {
                    "street": {"type": "string"},
                    "city": {"type": "string"},
                    "zip": {"type": "string"}
                },
                "required": ["street", "city"],
                "additionalProperties": false
            }
        }
    };
    test:assertEquals(check generate(AddressAlias, includeSchemaUri = false), expected);
    test:assertEquals(check generate(ReadOnlyAddress, includeSchemaUri = false), expected);
    test:assertEquals(check generate(AddressReadOnly, includeSchemaUri = false), expected);
}

@test:Config
function testRecursiveUnionAndTuple() returns error? {
    test:assertEquals(check generate(JsonLike, includeSchemaUri = false), {
        "anyOf": [
            {"type": "boolean"},
            {"type": "integer"},
            {"type": "array", "items": {"$ref": "#"}},
            {"type": "object", "additionalProperties": {"$ref": "#"}},
            {"type": "null"}
        ]
    });
    test:assertEquals(check generate(Tree, includeSchemaUri = false), {
        "type": "array",
        "prefixItems": [{"type": "integer"}, {"type": "array", "items": {"$ref": "#"}}],
        "items": false,
        "minItems": 2
    });
}

@test:Config
function testLiteralsAndUnions() returns error? {
    test:assertEquals(check generate(One, includeSchemaUri = false), {"const": "one"});
    test:assertEquals(check generate(Never, includeSchemaUri = false), {"not": {}});
    test:assertEquals(check generate(MaybeLiteral, includeSchemaUri = false), {"enum": ["x", ()]});
    test:assertEquals(check generate(IntOrString, includeSchemaUri = false),
            {"anyOf": [{"type": "integer"}, {"type": "string"}]});
    test:assertEquals(check generate(IntsOnly, includeSchemaUri = false), {"type": "array", "items": {"type": "integer"}});
    test:assertEquals(check generate(AllOptional, includeSchemaUri = false), {
        "type": "object",
        "properties": {"a": {"type": "integer"}},
        "additionalProperties": false
    });
}

@test:Config
function testMiscTypes() returns error? {
    map<json> schema = check generate(Misc, includeSchemaUri = false);
    test:assertEquals(schema, {
        "type": "object",
        "properties": {
            "s16": {"type": "integer", "minimum": -32768, "maximum": 32767},
            "s32": {"type": "integer", "minimum": -2147483648, "maximum": 2147483647},
            "u8": {"type": "integer", "minimum": 0, "maximum": 255},
            "u32": {"type": "integer", "minimum": 0, "maximum": 4294967295},
            "regex": {"type": "string", "format": "regex"},
            "pair": {
                "type": "array",
                "prefixItems": [{"type": "integer"}, {"type": "string"}],
                "items": false,
                "minItems": 2
            }
        },
        "required": ["s16", "s32", "u8", "u32", "regex", "pair"],
        "additionalProperties": false
    });
}

@test:Config
function testNameClashAcrossModules() returns error? {
    map<json> schema = check generate(NameClash, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["mine"], {"$ref": "#/$defs/StringConstraints"});
    test:assertEquals(properties["theirs"], {"$ref": "#/$defs/constraint_StringConstraints"});
    map<json> defs = check schema["$defs"].ensureType();
    test:assertEquals(defs.keys(), ["StringConstraints", "constraint_StringConstraints"]);
}

@test:Config
function testMoreConstraints() returns error? {
    map<json> schema = check generate(MoreConstraints, includeSchemaUri = false, inlineRefs = true);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["amount"], {"type": "number", "minimum": 1, "maximum": 9.5d});
    test:assertEquals(properties["code"], {"type": "string", "minLength": 3, "maxLength": 3});
    map<json> birthday = check properties["birthday"].ensureType();
    test:assertEquals(birthday["type"], "object");
    test:assertFalse(birthday.hasKey("format"));
}

@test:Config
function testExamplesAndEscaping() returns error? {
    string compact = check generateString(Examples, false, includeSchemaUri = false);
    test:assertEquals(compact, string `{"title":"Examples","type":"object","properties":{"text":{"description":` +
            string `"Doc description","type":"string","deprecated":false}},"required":["text"],` +
            string `"additionalProperties":false,"examples":[null,true,42,1.5,2.25,null,null,` +
            string `"quote \" backslash \\ newline \n return \r tab \t backspace \b formfeed \f control \u0001",` +
            string `[],{},[1,{"a":[true]}]]}`);

    string pretty = check generateString(Examples, includeSchemaUri = false);
    test:assertTrue(pretty.includes("\n    \"text\": {\n      \"description\": \"Doc description\","), pretty);
    test:assertTrue(pretty.includes("\n    [],\n    {},\n"), pretty);
}

@test:Config
function testToSchemaMapErrors() returns error? {
    test:assertEquals(check toSchemaMap(string `{"type": "integer"}`), {"type": "integer"});

    map<json>|Error invalid = toSchemaMap("{");
    if invalid !is Error {
        return error("expected an error for invalid JSON");
    }
    test:assertTrue(invalid.message().startsWith("generated schema is not valid JSON: "), invalid.message());
    test:assertTrue(invalid.cause() is error, "the parse error must be kept as the cause");

    map<json>|Error notAnObject = toSchemaMap("[1, 2]");
    if notAnObject !is Error {
        return error("expected an error for a JSON array");
    }
    test:assertEquals(notAnObject.message(), "generated schema is not a JSON object");
}
