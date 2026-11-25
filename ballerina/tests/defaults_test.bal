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

// The compiler plugin doesn't run on this package's own tests, so `@Default` is written by hand here.
// End-to-end tests of the generated annotations are in `ballerina-tests`.

type DefaultPoint record {|
    @Default {value: 0}
    int x = 0;
    int y = nextY();
    @Default {value: "origin"}
    string label = "origin";
|};

isolated function nextY() returns int => 1;

type Row record {|
    readonly int id;
|};

type RowTable table<Row> key(id);

type WithDefaults record {|
    @Default {value: ()}
    int? nilValue = ();
    @Default {value: 1}
    int intValue = 1;
    @Default {value: true}
    boolean booleanValue = true;
    @Default {value: 1.5}
    float floatValue = 1.5;
    @Default {value: 2.5d}
    decimal decimalValue = 2.5d;
    @Default {value: "s"}
    string stringValue = "s";
    @Default {value: 7}
    byte byteValue = 7;
    @Default {value: [1, 2]}
    int[] listValue = [1, 2];
    @Default {value: {a: 1}}
    map<int> mapValue = {a: 1};
    @Default {value: table [{id: 1}]}
    RowTable tableValue = table [{id: 1}];
    // Defaults that can't be represented in JSON are not emitted.
    @Default {value: float:NaN}
    float nanValue = float:NaN;
    @Default {value: [float:Infinity]}
    float[] infiniteMember = [float:Infinity];
    @Default {value: {a: -float:Infinity}}
    map<float> infiniteValue = {a: -float:Infinity};
    @Default {value: xml `<a/>`}
    anydata xmlValue = xml `<a/>`;
    @Default {value: table [{id: 1, "f": float:NaN}]}
    table<map<anydata>> nanRow = table [{id: 1, "f": float:NaN}];
    // Nested defaults are completed from the `@Default` annotations of the record's fields.
    @Default {value: {}}
    DefaultPoint point = {};
    @Default {value: {y: 5}}
    DefaultPoint pointWithY = {y: 5};
    @Default {value: [{}, {x: 3}]}
    DefaultPoint[] points = [{}, {x: 3}];
    @Default {value: {p: {}}}
    record {| DefaultPoint p; |} nested = {p: {}};
    @Default {value: [{}]}
    [DefaultPoint] pointTuple = [{}];
    @Default {value: <byte[]>[1, 2]}
    byte[] bytes = [1, 2];
    int noAnnotation = 3;
|};

@test:Config
function testDefaultValues() returns error? {
    map<json> schema = check generate(WithDefaults, includeSchemaUri = false, inlineRefs = true);
    map<json> properties = check schema.properties.ensureType();
    map<json> defaults = {};
    foreach [string, json] [name, property] in properties.entries() {
        map<json> propertySchema = check property.ensureType();
        if propertySchema.hasKey("default") {
            defaults[name] = propertySchema["default"];
        }
    }
    test:assertEquals(defaults, {
        nilValue: (),
        intValue: 1,
        booleanValue: true,
        floatValue: 1.5d,
        decimalValue: 2.5d,
        stringValue: "s",
        byteValue: 7,
        listValue: [1, 2],
        mapValue: {a: 1},
        tableValue: [{id: 1}],
        bytes: [1, 2],
        // `y` has a function call default, so it has no `@Default` and stays missing.
        point: {x: 0, label: "origin"},
        pointWithY: {y: 5, x: 0, label: "origin"},
        points: [{x: 0, label: "origin"}, {x: 3, label: "origin"}],
        nested: {p: {x: 0, label: "origin"}},
        // Tuples are not completed.
        pointTuple: [{}]
    });
    test:assertFalse(defaults.hasKey("noAnnotation"));
    test:assertEquals(check schema.properties.nilValue, {"anyOf": [{"type": "integer"}, {"type": "null"}], "default": ()});
}

@test:Config
function testDefaultWithRef() returns error? {
    map<json> schema = check generate(WithDefaults, includeSchemaUri = false);
    test:assertEquals(check schema.properties.point, {"$ref": "#/$defs/DefaultPoint", "default": {x: 0, label: "origin"}});
    test:assertEquals(check schema.\$defs.DefaultPoint.properties.x, {"type": "integer", "default": 0});
    test:assertEquals(check schema.\$defs.DefaultPoint.properties.y, {"type": "integer"});
}

// ---------------------------------------------------------------- constraints of inlined types are merged

@constraint:String {minLength: 5, maxLength: 10, pattern: re `[a-z]+`}
type Code string;

@constraint:String {pattern: re `[a-c]+`}
type ShortCode Code;

@constraint:Int {minValue: 10, maxValue: 100}
type Percent int;

@constraint:Float {minValueExclusive: 0.5, maxValue: 9.5}
type Ratio float;

@constraint:Number {minValue: 1.5d}
type Amount decimal;

@constraint:Array {minLength: 1, maxLength: 3}
type Tags string[];

type MergedConstraints record {|
    // weaker field constraints: the type's stricter ones are kept
    @constraint:String {minLength: 2, maxLength: 20}
    Code weaker;
    // stricter field constraints replace the type's
    @constraint:String {minLength: 7, maxLength: 8}
    Code stricter;
    // a different pattern: both must match
    @constraint:String {pattern: re `[a-m]+`}
    Code otherPattern;
    // the same pattern is not repeated
    @constraint:String {pattern: re `[a-z]+`}
    Code samePattern;
    // a pattern already in `allOf` is not repeated
    @constraint:String {pattern: re `[a-c]+`}
    ShortCode repeatedAllOfPattern;
    @constraint:Int {minValue: 0, maxValue: 50}
    Percent percent;
    @constraint:Float {minValueExclusive: 1.5, maxValue: 20.0}
    Ratio ratio;
    @constraint:Number {minValue: 1.0d}
    Amount amount;
    @constraint:Number {minValue: 2.5d}
    Amount largerAmount;
    @constraint:Array {minLength: 2, maxLength: 5}
    Tags tags;
    // built-in ranges are merged too
    @constraint:Int {minValue: 10, maxValue: 1000}
    byte small;
|};

@test:Config
function testConstraintsOfInlinedTypesAreMerged() returns error? {
    map<json> schema = check generate(MergedConstraints, includeSchemaUri = false, inlineRefs = true);
    test:assertEquals(schema["properties"], {
        "weaker": {"type": "string", "minLength": 5, "maxLength": 10, "pattern": "[a-z]+"},
        "stricter": {"type": "string", "minLength": 7, "maxLength": 8, "pattern": "[a-z]+"},
        "otherPattern": {"type": "string", "minLength": 5, "maxLength": 10, "pattern": "[a-z]+",
            "allOf": [{"pattern": "[a-m]+"}]},
        "samePattern": {"type": "string", "minLength": 5, "maxLength": 10, "pattern": "[a-z]+"},
        "repeatedAllOfPattern": {"type": "string", "minLength": 5, "maxLength": 10, "pattern": "[a-z]+",
            "allOf": [{"pattern": "[a-c]+"}]},
        "percent": {"type": "integer", "minimum": 10, "maximum": 50},
        "ratio": {"type": "number", "exclusiveMinimum": 1.5d, "maximum": 9.5d},
        "amount": {"type": "number", "minimum": 1.5d},
        "largerAmount": {"type": "number", "minimum": 2.5d},
        "tags": {"type": "array", "items": {"type": "string"}, "minItems": 2, "maxItems": 3},
        "small": {"type": "integer", "minimum": 10, "maximum": 255}
    });

    // An alias of a named type keeps its own constraints next to the $ref (and doesn't repeat the referred type's).
    test:assertEquals(check generate(ShortCode, includeSchemaUri = false), {
        "$ref": "#/$defs/Code",
        "pattern": "[a-c]+",
        "$defs": {"Code": {"type": "string", "minLength": 5, "maxLength": 10, "pattern": "[a-z]+"}}
    });

    // With $defs, the field's constraints sit next to the $ref and both apply.
    schema = check generate(MergedConstraints, includeSchemaUri = false);
    test:assertEquals(check schema.properties.weaker, {"$ref": "#/$defs/Code", "minLength": 2, "maxLength": 20});
}
