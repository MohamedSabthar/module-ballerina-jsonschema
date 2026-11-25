import ballerina/jsonschema as js;
import plugin_tests.util;

const int MAX = 3;
final string region = "us-east-1";
configurable int timeout = 30;
final int negative = -5;
enum Color { RED, GREEN }

isolated function nextId() returns int => 1;

type Defaults record {|
    int intLiteral = 1;
    string stringLiteral = "s";
    boolean booleanLiteral = true;
    int? nilLiteral = ();
    json nullLiteral = null;
    decimal negativeLiteral = -1.5;
    int braced = (2);
    int constant = MAX;
    string finalVariable = region;
    int configurableVariable = timeout;
    Color enumMember = RED;
    int otherModuleConstant = util:REMOTE_MAX;
    string otherModuleFinal = util:remoteRegion;
    int[] list = [1, MAX, -2];
    map<anydata> mapping = {a: 1, "b": region, c: [MAX, RED], timeout};
    int trailingComment = 4; // a comment
    @js:Default {value: 99}
    int existingDefault = 5;
    # Documented and defaulted
    int documented = 6;
    int functionCall = nextId();
    int binary = MAX + 1;
    string template = string `t`;
    int unaryPlus = +1;
    int negatedReference = -negative;
    int[] listSpread = [...[1, 2]];
    map<int> mappingSpread = {...{a: 1}};
    map<int> computedKey = {[region]: 1};
    float typeCast = <float>1;
    int[] unsafeMember = [nextId()];
    map<int> unsafeValue = {a: nextId()};
    typedesc<anydata> typeReference = Color;
|};

function localType() returns int {
    final int localFinal = 7;
    record {| int fromLocal = localFinal; int literal = 8; |} r = {};
    return r.fromLocal + r.literal;
}
