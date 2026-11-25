/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.jsonschema;

/**
 * Thrown when a Ballerina type cannot be represented as a JSON Schema.
 *
 * @since 0.1.0
 */
public class SchemaGenerationException extends RuntimeException {
    private final String reason;
    private final String path;

    public SchemaGenerationException(String reason) {
        this(reason, "");
    }

    private SchemaGenerationException(String reason, String path) {
        super(path.isEmpty() ? reason : reason + " at '$" + path + "'");
        this.reason = reason;
        this.path = path;
    }

    /**
     * Returns this error with a path segment (e.g. {@code .field} or {@code [*]}) added in front of its path.
     */
    SchemaGenerationException at(String pathSegment) {
        return new SchemaGenerationException(reason, pathSegment + path);
    }
}
