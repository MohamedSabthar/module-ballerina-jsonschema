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

import ballerina/jsonschema as js;
import ballerina/test;

# Line one
# continues on line two.
#
# Second paragraph.
#
# ```ballerina
# int x = 1;
#     indented();
# ```
#
# + a - Param doc for a
# + b - Param doc for b
# + missing - Documents a field that doesn't exist
# # Deprecated
# This text is not part of the description.
type Formatting record {|
    # Field doc wins over param doc
    int a;
    int b?;
    int c = 3;
|};

# + x - Only parameter docs
type OnlyParams record {|
    int x;
|};

#
type EmptyDoc record {|
    int x;
|};

# Unicode: € 日本語 😀, backticks `code`, braces {x}, template ${x}, quotes "q", backslash \ and	tab.
type SpecialChars record {|
    int x;
|};

# Union of records.
#
# + id - Identifier in both members
type RecordUnion record {|
    int id;
|}|record {|
    string id;
|};

# Immutable record.
#
# + name - The name
type Immutable readonly & record {|
    string name;
|};

# A list of names.
type Names string[];

# A score between 0 and 100.
type Score int;

type UsesDocumentedAlias record {|
    # The player's score
    Score score;
|};

# Base record.
type DocBase record {|
    # Identifier from the base
    string id;
|};

type DocDerived record {|
    *DocBase;
    # Own field
    string name;
|};

# Traffic light colors.
enum Light {
    RED,
    GREEN
}

@test:Config
function testFormatting() returns error? {
    map<json> schema = check js:generate(Formatting, includeSchemaUri = false);
    test:assertEquals(schema["description"], "Line one continues on line two.\n\nSecond paragraph.\n\n"
            + "```ballerina\nint x = 1;\n    indented();\n```");
    test:assertEquals(schema["properties"], {
        "a": {"description": "Field doc wins over param doc", "type": "integer"},
        "b": {"description": "Param doc for b", "type": "integer"},
        "c": {"type": "integer", "default": 3}
    });
}

@test:Config
function testOnlyParameterDocs() returns error? {
    map<json> schema = check js:generate(OnlyParams, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("description"));
    test:assertEquals(schema["properties"], {"x": {"description": "Only parameter docs", "type": "integer"}});
}

@test:Config
function testEmptyDoc() returns error? {
    map<json> schema = check js:generate(EmptyDoc, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("description"));
}

@test:Config
function testSpecialCharacters() returns error? {
    map<json> schema = check js:generate(SpecialChars, includeSchemaUri = false);
    test:assertEquals(schema["description"], "Unicode: € 日本語 😀, backticks `code`, braces {x}, template ${x}, "
            + "quotes \"q\", backslash \\ and\ttab.");
}

@test:Config
function testParameterDocsApplyToAllMembersOfAUnion() returns error? {
    map<json> schema = check js:generate(RecordUnion, includeSchemaUri = false);
    test:assertEquals(schema["description"], "Union of records.");
    json[] members = check schema["anyOf"].ensureType();
    test:assertEquals(members.length(), 2);
    foreach json member in members {
        json id = check member.properties.id;
        test:assertEquals(check id.description, "Identifier in both members", member.toJsonString());
    }
}

@test:Config
function testReadonlyIntersection() returns error? {
    map<json> schema = check js:generate(Immutable, includeSchemaUri = false);
    test:assertEquals(schema["description"], "Immutable record.");
    test:assertEquals(schema["properties"], {"name": {"description": "The name", "type": "string"}});
}

@test:Config
function testNonRecordTypes() returns error? {
    test:assertEquals(check js:generate(Names, includeSchemaUri = false),
            {"description": "A list of names.", "type": "array", "items": {"type": "string"}});
    test:assertEquals(check js:generate(Score, includeSchemaUri = false),
            {"description": "A score between 0 and 100.", "type": "integer"});
}

@test:Config
function testFieldAndTypeDocsOnReference() returns error? {
    map<json> schema = check js:generate(UsesDocumentedAlias, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {"score": {"description": "The player's score", "$ref": "#/$defs/Score"}});
    test:assertEquals(schema["$defs"], {"Score": {"description": "A score between 0 and 100.", "type": "integer"}});
}

// Known limitation: the Ballerina runtime doesn't carry the annotations of fields included with `*T` into the
// including record, so their descriptions (and any other field annotations) are only available on `T` itself.
@test:Config
function testIncludedFieldDocsAreNotInherited() returns error? {
    map<json> schema = check js:generate(DocDerived, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("description"), "the base record's doc must not leak into the derived record");
    test:assertEquals(schema["properties"], {
        "name": {"description": "Own field", "type": "string"},
        "id": {"type": "string"}
    });
    map<json> base = check js:generate(DocBase, includeSchemaUri = false);
    test:assertEquals(base["properties"], {"id": {"description": "Identifier from the base", "type": "string"}});
}

// Known limitation: the Ballerina runtime doesn't keep annotations of `enum` declarations, so enum doc comments
// can't become descriptions. Use a union of string literals (`type Light "RED"|"GREEN";`) to get one.
@test:Config
function testEnumDocIsNotAvailable() returns error? {
    map<json> schema = check js:generate(Light, includeSchemaUri = false);
    test:assertFalse(schema.hasKey("description"));
    assertEnum(schema, ["RED", "GREEN"]);
}

# Traffic light colors as literals.
type LightLiterals "RED"|"GREEN";

@test:Config
function testLiteralUnionDoc() returns error? {
    test:assertEquals(check js:generate(LightLiterals, includeSchemaUri = false),
            {"description": "Traffic light colors as literals.", "enum": ["RED", "GREEN"]});
}

function assertEnum(map<json> schema, json[] expected) {
    json[] actual = <json[]>schema["enum"];
    test:assertEquals(actual.length(), expected.length(), schema.toJsonString());
    foreach json value in expected {
        test:assertTrue(actual.indexOf(value) != (), schema.toJsonString());
    }
}
