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

import ballerina/jsonschema;
import ballerina/test;

# A customer of the store.
#
# Customers are created on sign up.
#
# + email - Primary contact email
# + tier - Loyalty tier
type Customer record {|
    # Full name of the customer
    string name;
    string email;
    string tier = "basic";
    # Shipping address
    Address address;
    # Overridden by Meta
    @jsonschema:Meta {description: "Explicit description"}
    string nickname?;
    string undocumented?;
|};

# A postal address with "quotes" and a \ backslash.
type Address record {|
    # Street line
    string street;
    # Nested anonymous record
    record {|
        # Postal code
        string code;
    |} postal;
|};

# Shirt size.
type Size "S"|"M"|"L";

@test:Config
function testTypeDescriptionFromDocComment() returns error? {
    map<json> schema = check jsonschema:generate(Customer, includeSchemaUri = false);
    test:assertEquals(schema["description"], "A customer of the store.\n\nCustomers are created on sign up.");
}

@test:Config
function testFieldDescriptionsFromDocComments() returns error? {
    map<json> schema = check jsonschema:generate(Customer, includeSchemaUri = false);
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["name"], {"description": "Full name of the customer", "type": "string"});
    // `# + field - ...` lines on the type definition
    test:assertEquals(properties["email"], {"description": "Primary contact email", "type": "string"});
    test:assertEquals(properties["tier"], {"description": "Loyalty tier", "type": "string", "default": "basic"});
    // A description on a field that refers to a named type sits next to the `$ref`
    test:assertEquals(properties["address"], {"description": "Shipping address", "$ref": "#/$defs/Address"});
    // `@jsonschema:Meta` takes precedence over the doc comment
    test:assertEquals(properties["nickname"], {"description": "Explicit description", "type": "string"});
    test:assertEquals(properties["undocumented"], {"type": "string"});
}

@test:Config
function testNestedDescriptionsAndEscaping() returns error? {
    map<json> schema = check jsonschema:generate(Address, includeSchemaUri = false);
    test:assertEquals(schema["description"], "A postal address with \"quotes\" and a \\ backslash.");
    map<json> properties = check schema.properties.ensureType();
    test:assertEquals(properties["street"], {"description": "Street line", "type": "string"});
    map<json> postal = check properties["postal"].ensureType();
    test:assertEquals(postal["description"], "Nested anonymous record");
    map<json> postalProperties = check postal.properties.ensureType();
    test:assertEquals(postalProperties["code"], {"description": "Postal code", "type": "string"});
}

@test:Config
function testNonRecordTypeDescription() returns error? {
    map<json> schema = check jsonschema:generate(Size, includeSchemaUri = false);
    test:assertEquals(schema["description"], "Shirt size.");
}
