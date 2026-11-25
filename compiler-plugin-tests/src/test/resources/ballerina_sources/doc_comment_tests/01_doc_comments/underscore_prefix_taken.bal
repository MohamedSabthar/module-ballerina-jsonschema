import ballerina/jsonschema as _;
import ballerina/io as jsonschema;
import plugin_tests.util;

# The `jsonschema` prefix is taken in this file.
type Taken record {|
    int x;
|};

function useImports() {
    jsonschema:println(util:greet());
}
