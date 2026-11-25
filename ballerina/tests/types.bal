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

type Nil ();

type OptionalInt int?;

type Address record {|
    string street;
    string city;
    string zip?;
|};

type Person record {|
    string name;
    int age;
    float? height;
    boolean active = true;
    Address address;
    Address[] previousAddresses?;
    readonly string id;
|};

type OpenRecord record {
    string name;
};

type OpenRecordWithRest record {|
    string name;
    int...;
|};

type TreeNode record {|
    string value;
    TreeNode[] children;
|};

type LinkedList record {|
    int value;
    LinkedList? next;
|};

type Wrapper record {|
    LinkedList list;
|};

enum Color {
    RED,
    GREEN,
    BLUE
}

type Status "active"|"inactive"|"pending";

type Point [int, int];

type Path [string, int...];

type Pixel record {|
    Color color;
    Status status;
    Point point;
    Path path;
    Color? optionalColor;
|};

@constraint:String {minLength: 2, maxLength: 10, pattern: re `[a-z]+`}
type Username string;

type Constrained record {|
    @constraint:String {length: 5}
    string code;
    @constraint:Int {minValue: 0, maxValue: 120}
    int age;
    @constraint:Float {minValueExclusive: 0.0, maxValueExclusive: 1.0}
    float ratio;
    @constraint:Array {minLength: 1, maxLength: 3}
    string[] tags;
    Username username;
|};

@Meta {title: "Product", description: "A product in the catalog", examples: [{"sku": "A1"}]}
type Product record {|
    @Meta {description: "Stock keeping unit", format: "uuid"}
    string sku;
    @Meta {deprecated: true}
    string legacyCode?;
    @Description {value: "Price in USD"}
    decimal price;
    @Meta {description: "Meta wins"}
    @Description {value: "Doc loses"}
    string name;
|};

type Numbers record {|
    byte b;
    int:Signed8 s8;
    int:Unsigned16 u16;
    string:Char ch;
    decimal d;
    int[3] fixed;
    map<string> labels;
    map<json> extra;
    json anything;
|};

type Item record {|
    readonly int id;
    string name;
|};

type ItemTable table<Item> key(id);

type ReadOnlyPerson readonly & record {|
    string name;
|};

type WithXml record {|
    xml content;
|};

type WithAnonymous record {|
    record {|
        int a;
    |} inner;
|};

type AddressAlias Address;

type ReadOnlyAddress readonly & Address;

type AddressReadOnly Address & readonly;

type JsonLike ()|boolean|int|JsonLike[]|map<JsonLike>;

type Tree [int, Tree[]];

type IntsOnly [int...];

type One "one";

type Never never;

type MaybeLiteral "x"|();

type IntOrString int|string;

type AllOptional record {|
    int a?;
|};

type Misc record {|
    int:Signed16 s16;
    int:Signed32 s32;
    int:Unsigned8 u8;
    int:Unsigned32 u32;
    string:RegExp regex;
    [int, string] pair;
    never forbidden?;
|};

type StringConstraints record {|
    string a;
|};

type NameClash record {|
    StringConstraints mine;
    constraint:StringConstraints theirs;
|};

type DateRecord record {|
    int year;
    int month;
    int day;
|};

type MoreConstraints record {|
    @constraint:Number {minValue: 1, maxValue: 9.5}
    decimal amount;
    @constraint:String {length: {value: 3, message: "must have 3 characters"}}
    string code;
    @constraint:Date {option: constraint:PAST}
    DateRecord birthday;
|};

@Meta {
    title: "Examples",
    examples: [
        (),
        true,
        42,
        1.5,
        2.25d,
        float:NaN,
        float:Infinity,
        "quote \" backslash \\ newline \n return \r tab \t backspace \u{8} formfeed \u{C} control \u{1}",
        [],
        {},
        [1, {"a": [true]}]
    ]
}
type Examples record {|
    @Meta {deprecated: false}
    @Description {value: "Doc description"}
    string text;
|};
