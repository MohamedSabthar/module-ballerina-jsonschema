import ballerina/jsonschema as _;

# A type in a file that imports the library only for its compiler plugin.
#
# + y - The y value
type Hidden record {|
    # The x value
    int x;
    int y;
|};
