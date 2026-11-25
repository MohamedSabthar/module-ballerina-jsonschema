import ballerina/jsonschema;

# Has a compilation error.
type Broken record {|
    # A field
    int x;
|};

final jsonschema:Options options = {};

int invalid = "not an int";
