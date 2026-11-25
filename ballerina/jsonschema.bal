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

# The JSON Schema dialect URI emitted in the `$schema` keyword.
public const SCHEMA_DIALECT = "https://json-schema.org/draft/2020-12/schema";

# Options for JSON Schema generation.
public type Options record {|
    # Whether to include the `$schema` keyword in the generated schema
    boolean includeSchemaUri = true;
    # Whether to inline named types instead of emitting them under `$defs` and referring to them with `$ref`.
    # Recursive types are always emitted under `$defs`.
    boolean inlineRefs = false;
|};

# Represents an error that occurs while generating a JSON Schema.
public type Error distinct error;

# Generates a JSON Schema (draft 2020-12) for the given Ballerina type.
#
# ```ballerina
# map<json> schema = check jsonschema:generate(Person);
# ```
#
# + t - The type to generate the schema for
# + options - Generation options
# + return - The JSON Schema as a `map<json>`, or a `jsonschema:Error` if the type
# (or one of its constituents) cannot be represented in JSON Schema (e.g. `xml`)
public isolated function generate(typedesc<anydata> t, *Options options) returns map<json>|Error {
    string schema = check externGenerate(t, options.includeSchemaUri, options.inlineRefs, false);
    return toSchemaMap(schema);
}

# Parses the JSON text produced by the native generator into a `map<json>`.
#
# + schema - The generated JSON text
# + return - The schema, or a `jsonschema:Error` if the text is not a JSON object
isolated function toSchemaMap(string schema) returns map<json>|Error {
    json|error parsed = schema.fromJsonString();
    if parsed is error {
        return error Error("generated schema is not valid JSON: " + parsed.message(), parsed);
    }
    if parsed !is map<json> {
        return error Error("generated schema is not a JSON object");
    }
    return parsed;
}

# Generates a JSON Schema (draft 2020-12) for the given Ballerina type as a JSON string.
#
# + t - The type to generate the schema for
# + pretty - Whether to pretty print the JSON string
# + options - Generation options
# + return - The JSON Schema as a string, or a `jsonschema:Error` if the type cannot be represented in JSON Schema
public isolated function generateString(typedesc<anydata> t, boolean pretty = true, *Options options)
        returns string|Error => externGenerate(t, options.includeSchemaUri, options.inlineRefs, pretty);
