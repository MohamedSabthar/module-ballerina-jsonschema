# Ballerina JSON Schema Library

[![Build](https://github.com/MohamedSabthar/module-ballerina-jsonschema/actions/workflows/build-timestamped-master.yml/badge.svg)](https://github.com/MohamedSabthar/module-ballerina-jsonschema/actions/workflows/build-timestamped-master.yml)
[![codecov](https://codecov.io/gh/MohamedSabthar/module-ballerina-jsonschema/branch/main/graph/badge.svg)](https://codecov.io/gh/MohamedSabthar/module-ballerina-jsonschema)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/MohamedSabthar/module-ballerina-jsonschema.svg)](https://github.com/MohamedSabthar/module-ballerina-jsonschema/commits/main)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Overview

This module provides APIs for generating [JSON Schema (draft 2020-12)](https://json-schema.org/draft/2020-12) documents from Ballerina types.

The schema is generated at runtime from a `typedesc`, and covers records, maps, arrays, tuples, tables, unions, enums, singletons and recursive types. Doc comments become `description` keywords and default values become `default` keywords (with the help of a compiler plugin), `ballerina/constraint` annotations become keywords such as `minLength`, `maximum` and `pattern`, and the `@jsonschema:Meta` annotation adds `title`, `examples`, `format` and `deprecated`.

```ballerina
import ballerina/jsonschema;

# A customer of the store.
type Customer record {|
    # Full name of the customer
    string name;
    int age?;
|};

public function main() returns error? {
    map<json> schema = check jsonschema:generate(Customer);
}
```

For the full type mapping and options, see the [module documentation](ballerina/README.md).

## Issues and projects

To report bugs, request new features or start new discussions, use the [Issues](https://github.com/MohamedSabthar/module-ballerina-jsonschema/issues) tab of this repository.

## Build from the source

### Prerequisites

1. Download and install Java SE Development Kit (JDK) version 25 (from one of the following locations).

   - [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - [OpenJDK](https://adoptium.net/)

     > **Note:** Set the JAVA_HOME environment variable to the path name of the directory into which you installed JDK.

2. Generate a GitHub access token with read package permissions, then set the following `env` variables:

   ```shell
   export packageUser=<Your GitHub Username>
   export packagePAT=<GitHub Personal Access Token>
   ```

### Build options

Execute the commands below to build from the source.

1. To build the package:

   ```bash
   ./gradlew clean build
   ```

2. To run the tests:

   ```bash
   ./gradlew clean test
   ```

3. To run a group of tests:

   ```bash
   ./gradlew clean test -Pgroups=<test_group_names>
   ```

4. To build the package without the tests:

   ```bash
   ./gradlew clean build -x test
   ```

5. To debug the package with a remote debugger:

   ```bash
   ./gradlew clean build -Pdebug=<port>
   ```

6. To debug with Ballerina language:

   ```bash
   ./gradlew clean build -PbalJavaDebug=<port>
   ```

7. Publish the generated artifacts to the local Ballerina central repository:

   ```bash
   ./gradlew clean build -PpublishToLocalCentral=true
   ```

8. Publish the generated artifacts to the Ballerina central repository:

   ```bash
   ./gradlew clean build -PpublishToCentral=true
   ```

## Contribute to Ballerina

As an open-source project, Ballerina welcomes contributions from the community.

For more information, go to the [contribution guidelines](https://github.com/ballerina-platform/ballerina-lang/blob/master/CONTRIBUTING.md).

## Code of conduct

All the contributors are encouraged to read the [Ballerina Code of Conduct](https://ballerina.io/code-of-conduct).

## Useful links

- For more information go to the [`jsonschema` module documentation](ballerina/README.md).
- Chat live with us via our [Discord server](https://discord.gg/ballerinalang).
- Post all technical questions on Stack Overflow with the [#ballerina](https://stackoverflow.com/questions/tagged/ballerina) tag.
