import ballerina/io;
import ballerina/lang.value;
import plugin_tests.util;
import testorg/plugin_tests.marker;
import ballerina/jsonschema as js;

# A user.
#
# Second paragraph with `code`.
# ```
# int x = 1;
# ```
# After code.
#
# + email - Contact
# email address
# + 'type - The kind
# + nothing -
# + return - ignored
# # Deprecated
# Old type.
@js:Meta {title: "User"}
type User record {|
    # Display name
    @marker:Marker
    @display {label: "Name"}
    string name;
    string email;
    string 'type = "x";
    # Ignored doc
    @js:Description {value: "Explicit"}
    string explicit;
    @js:Meta {title: "No doc"}
    string noDoc;
    # Nested record
    record {|
        # Inner field
        int a;
        # Field with default
        int b = 1;
        int c = 2;
    |} nested;
|};

# Already described type.
@js:Description {value: "Explicit type"}
type Described int;

type NoDocs record {|
    int x;
|};

# Escaped "quotes", \ backslash and	tab.
type Name string;

# Union of records.
#
# + id - Identifier
type IdUnion record {|
    int id;
    # Nested
    record {|
        int id;
    |} inner;
|}|record {|
    string id;
|};

# + id - Read-only identifier
type ReadOnlyIds readonly & record {|
    int id;
|};

public function main() {
    value:Cloneable c = 1;
    io:println(util:greet(), c);
}
