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

import ballerina/jsonschema as js;
import ballerina/test;

const int MAX_RETRIES = 3;
final string defaultRegion = "us-east-1";
// Overridden to 45 in tests/Config.toml.
configurable int timeout = 30;
isolated int counter = 0;

enum Level {
    LOW,
    HIGH = "high"
}

isolated function nextId() returns int {
    lock {
        counter += 1;
        return counter;
    }
}

type Settings record {|
    string name;
    int retries = MAX_RETRIES;
    string region = defaultRegion;
    int timeoutSeconds = timeout;
    string declaredLater = laterValue;
    Level level = HIGH;
    int fromOtherModule = models:MODEL_MAX;
    int id = nextId();
    boolean enabled = true;
    string[] tags = ["a", defaultRegion];
    int? note = ();
    decimal ratio = -0.5;
    record {| int port = 8080; string host; int seq = nextId(); |} inlineServer = {host: "example.com"};
    models:Server server = {};
    map<string> labels = {env: "dev", region: defaultRegion};
    int computed = MAX_RETRIES * 2;
|};

final string laterValue = "declared after the type";

@test:Config
function testDefaultsMatchBallerinaValues() returns error? {
    map<json> schema = check js:generate(Settings, includeSchemaUri = false, inlineRefs = true);
    map<json> properties = check schema.properties.ensureType();
    Settings actual = {name: "x"};
    map<json> actualJson = check actual.toJson().ensureType();
    string[] withDefault = [];
    foreach [string, json] [name, property] in properties.entries() {
        map<json> propertySchema = check property.ensureType();
        if !propertySchema.hasKey("default") {
            continue;
        }
        withDefault.push(name);
        json expected = name == "inlineServer"
            // `seq` has a function call default, so it isn't part of the schema default.
            ? {host: "example.com", port: 8080}
            : actualJson[name];
        test:assertEquals(propertySchema["default"].toJsonString(), expected.toJsonString(), name);
    }
    test:assertEquals(withDefault, ["retries", "region", "timeoutSeconds", "declaredLater", "level",
        "fromOtherModule", "enabled", "tags", "note", "ratio", "inlineServer", "server", "labels"]);
    test:assertEquals(check schema.properties.timeoutSeconds.default, 45, "configurable value from Config.toml");
    test:assertEquals(check schema.properties.server.default, {port: 8080, host: "localhost"});
    test:assertEquals(schema["required"], ["name"]);
}

@test:Config
function testGeneratingTheSchemaDoesNotRunDefaultFunctions() returns error? {
    int before = nextId();
    _ = check js:generate(Settings);
    test:assertEquals(nextId(), before + 1, "a default value function was called");
}
