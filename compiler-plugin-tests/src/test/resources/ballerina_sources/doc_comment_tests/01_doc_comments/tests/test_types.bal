import ballerina/jsonschema;

# A test type.
type TestType record {|
    # A test field
    int x;
|};

final jsonschema:Options testOptions = {};
