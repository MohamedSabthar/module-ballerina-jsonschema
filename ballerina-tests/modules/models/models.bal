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

// A types-only module: it imports the library with the `_` prefix just to run the compiler plugin.
import ballerina/jsonschema as _;

# A circle.
public type Circle record {|
    # Discriminator
    "circle" kind;
    # Radius in cm
    float radius;
|};

# A square.
#
# + side - Side length in cm
public type Square record {|
    "square" kind;
    float side;
|};

# Any shape.
public type Shape Circle|Square;

# Address as defined in the models module.
public type Address record {|
    # Street in the models module
    string street;
|};

public const int MODEL_MAX = 9;

# Server settings.
public type Server record {|
    int port = 8080;
    string host = "localhost";
|};
