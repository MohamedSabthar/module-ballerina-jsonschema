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

# Additional JSON Schema metadata for a type or a record field.
public type MetaConfig record {|
    # Value for the `title` keyword
    string title?;
    # Value for the `description` keyword. Takes precedence over the doc comment
    string description?;
    # Value for the `examples` keyword
    json[] examples?;
    # Value for the `format` keyword (e.g. `date-time`, `email`, `uri`)
    string format?;
    # Value for the `deprecated` keyword
    boolean deprecated?;
|};

# Attaches JSON Schema metadata to a type definition or a record field.
#
# ```ballerina
# @jsonschema:Meta {title: "User", examples: [{"name": "Alice"}]}
# type User record {|
#     @jsonschema:Meta {format: "email"}
#     string email;
# |};
# ```
public annotation MetaConfig Meta on type, record field;

# Value of the `Description` annotation.
public type DescriptionConfig record {|
    # The description text
    string value;
|};

# Sets the `description` keyword of a type definition or a record field.
#
# This annotation is added automatically by the `jsonschema` compiler plugin from the doc comments
# (`# ...`) of type definitions and record fields, in every module that imports `ballerina/jsonschema`.
# It rarely needs to be written by hand.
public annotation DescriptionConfig Description on type, record field;

# Value of the `Default` annotation.
public type DefaultConfig record {|
    # The default value of the field
    anydata value;
|};

# Sets the `default` keyword of a record field.
#
# The `jsonschema` compiler plugin adds this annotation automatically to record fields whose default value
# expression has no side effects: literals, constants, enum members, module-level `final` and `configurable`
# variables, and list/mapping constructors of those. For other default values (e.g. function calls), it can be
# written by hand:
#
# ```ballerina
# type Order record {|
#     @jsonschema:Default {value: "pending"}
#     string status = initialStatus();
# |};
# ```
public annotation DefaultConfig Default on record field;
