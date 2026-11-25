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

import jsonschema_tests.models;
import jsonschema_tests.plain;

import ballerina/jsonschema as js;
import ballerina/test;

# Uses types from other modules.
type CrossModule record {|
    Address local; // defined in doc_comment_test.bal
    models:Address modelsAddress;
    plain:Address plainAddress;
    plain:Status status;
    models:Shape shape;
|};

@test:Config
function testUnderscoreImportedModuleDocs() returns error? {
    test:assertEquals(check js:generate(models:Shape, includeSchemaUri = false), {
        "description": "Any shape.",
        "anyOf": [{"$ref": "#/$defs/Circle"}, {"$ref": "#/$defs/Square"}],
        "$defs": {
            "Circle": {
                "description": "A circle.",
                "type": "object",
                "properties": {
                    "kind": {"description": "Discriminator", "const": "circle"},
                    "radius": {"description": "Radius in cm", "type": "number"}
                },
                "required": ["kind", "radius"],
                "additionalProperties": false
            },
            "Square": {
                "description": "A square.",
                "type": "object",
                "properties": {
                    "kind": {"const": "square"},
                    "side": {"description": "Side length in cm", "type": "number"}
                },
                "required": ["kind", "side"],
                "additionalProperties": false
            }
        }
    });
}

@test:Config
function testModuleWithoutImportHasNoDocs() returns error? {
    test:assertEquals(check js:generate(plain:Address, includeSchemaUri = false), {
        "type": "object",
        "properties": {"line1": {"type": "string"}, "number": {"type": "integer"}},
        "required": ["line1"],
        "additionalProperties": false
    });
}

@test:Config
function testSameTypeNameInDifferentModules() returns error? {
    map<json> schema = check js:generate(CrossModule, includeSchemaUri = false);
    test:assertEquals(schema["properties"], {
        "local": {"$ref": "#/$defs/Address"},
        "modelsAddress": {"$ref": "#/$defs/jsonschema_tests_models_Address"},
        "plainAddress": {"$ref": "#/$defs/jsonschema_tests_plain_Address"},
        "status": {"$ref": "#/$defs/Status"},
        "shape": {"$ref": "#/$defs/Shape"}
    });
    map<json> defs = check schema["$defs"].ensureType();
    test:assertEquals(defs["Address"], check js:generate(Address, includeSchemaUri = false));
    test:assertEquals(defs["jsonschema_tests_models_Address"], {
        "description": "Address as defined in the models module.",
        "type": "object",
        "properties": {"street": {"description": "Street in the models module", "type": "string"}},
        "required": ["street"],
        "additionalProperties": false
    });
    test:assertEquals(defs["jsonschema_tests_plain_Address"], check js:generate(plain:Address, includeSchemaUri = false));
    test:assertEquals(defs["Status"], {"enum": ["ACTIVE", "INACTIVE"]});
    string[] expectedDefs = ["Address", "Circle", "Shape", "Square", "Status", "jsonschema_tests_models_Address",
        "jsonschema_tests_plain_Address"];
    test:assertEquals(defs.keys().sort(), expectedDefs.sort());
}
