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

import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.ballerina.lib.jsonschema.Utils.CONSTRAINT_ANNOT_PREFIX;

/**
 * Maps {@code ballerina/constraint} annotations to JSON Schema validation keywords.
 *
 * @since 0.1.0
 */
final class ConstraintMapper {
    private static final String VALUE = "value";

    private ConstraintMapper() {
    }

    /**
     * Applies all constraint annotations found in {@code annotations} to {@code schema}.
     *
     * @param annotations annotation map of a type or a record field (keyed by annotation key)
     * @param schema      schema to add the keywords to
     */
    static void apply(BMap<BString, Object> annotations, Map<String, Object> schema) {
        for (BString bKey : annotations.getKeys()) {
            String key = bKey.getValue();
            if (!key.startsWith(CONSTRAINT_ANNOT_PREFIX)) {
                continue;
            }
            BMap<?, ?> config = (BMap<?, ?>) annotations.get(bKey);
            // Annotation keys look like `ballerina/constraint:1:String`.
            switch (key.substring(key.lastIndexOf(':') + 1)) {
                case "String" -> {
                    applyLength(config, schema, "minLength", "maxLength");
                    put(config, "pattern", schema, "pattern");
                }
                case "Int", "Float", "Number" -> {
                    put(config, "minValue", schema, "minimum");
                    put(config, "maxValue", schema, "maximum");
                    put(config, "minValueExclusive", schema, "exclusiveMinimum");
                    put(config, "maxValueExclusive", schema, "exclusiveMaximum");
                }
                case "Array" -> applyLength(config, schema, "minItems", "maxItems");
                default -> {
                    // `@constraint:Date` has no JSON Schema equivalent.
                }
            }
        }
    }

    private static void applyLength(BMap<?, ?> config, Map<String, Object> schema, String minKey, String maxKey) {
        put(config, "length", schema, minKey);
        put(config, "length", schema, maxKey);
        put(config, "minLength", schema, minKey);
        put(config, "maxLength", schema, maxKey);
    }

    private static void put(BMap<?, ?> config, String constraintField, Map<String, Object> schema,
                            String schemaKeyword) {
        Object value = Utils.get(config, constraintField);
        if (value == null) {
            return;
        }
        // Constraint values may be given as `{value: ..., message: "..."}` records.
        if (value instanceof BMap<?, ?> valueWithMessage) {
            value = Utils.get(valueWithMessage, VALUE);
        }
        merge(schema, schemaKeyword, Utils.toNode(value));
    }

    /**
     * Adds a keyword to the schema. If the schema already has it (e.g. a type's constraint when the type is inlined
     * into a field with its own constraint, or the range of {@code byte}), both must hold, so the stricter bound is
     * kept and a different pattern is added to {@code allOf}.
     */
    @SuppressWarnings("unchecked")
    static void merge(Map<String, Object> schema, String keyword, Object value) {
        Object existing = schema.get(keyword);
        if (existing == null || existing.equals(value)) {
            schema.put(keyword, value);
            return;
        }
        switch (keyword) {
            case "minLength", "minItems", "minimum", "exclusiveMinimum" -> {
                if (compare(value, existing) > 0) {
                    schema.put(keyword, value);
                }
            }
            case "maxLength", "maxItems", "maximum", "exclusiveMaximum" -> {
                if (compare(value, existing) < 0) {
                    schema.put(keyword, value);
                }
            }
            default -> {
                // `pattern`: a schema has a single pattern, so additional ones go into `allOf`.
                List<Object> allOf = (List<Object>) schema.computeIfAbsent("allOf", k -> new ArrayList<>());
                Map<String, Object> patternSchema = Map.of(keyword, value);
                if (!allOf.contains(patternSchema)) {
                    allOf.add(patternSchema);
                }
            }
        }
    }

    private static int compare(Object a, Object b) {
        return toBigDecimal(a).compareTo(toBigDecimal(b));
    }

    private static BigDecimal toBigDecimal(Object number) {
        // Numbers in schema nodes are Long, Double or BigDecimal.
        return number instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
    }
}
