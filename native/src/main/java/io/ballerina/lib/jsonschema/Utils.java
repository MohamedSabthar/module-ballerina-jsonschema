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

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for reading annotation values and converting Ballerina values to schema nodes.
 *
 * @since 0.1.0
 */
public final class Utils {
    static final String JSONSCHEMA_ANNOT_PREFIX = "ballerina/jsonschema:";
    static final String CONSTRAINT_ANNOT_PREFIX = "ballerina/constraint:";
    static final String FIELD_ANNOT_PREFIX = "$field$.";

    private Utils() {
    }

    /**
     * Returns true if the annotation key (e.g. {@code ballerina/constraint:1:String}) belongs to the given
     * module prefix and has the given annotation name.
     */
    static boolean isAnnotation(String key, String modulePrefix, String annotationName) {
        return key.startsWith(modulePrefix) && key.endsWith(":" + annotationName);
    }

    static Object get(BMap<?, ?> map, String key) {
        return map.get(StringUtils.fromString(key));
    }

    /** Marker for values that can't be represented in JSON. */
    static final Object NOT_JSON = new Object();

    /**
     * Converts a Ballerina value to a JSON node, or returns {@link #NOT_JSON} if the value (or a member of it) can't
     * be represented in JSON: {@code xml}, non-finite floats, etc. Tables become arrays of their rows.
     */
    static Object jsonNode(Object value) {
        if (value == null || value instanceof Long || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Double d) {
            return Double.isFinite(d) ? d : NOT_JSON;
        }
        if (value instanceof BString || value instanceof Integer || value instanceof BDecimal) {
            return toNode(value);
        }
        Iterable<?> members = value instanceof BArray array ? arrayMembers(array)
                : value instanceof BTable<?, ?> table ? table.values() : null;
        if (members != null) {
            List<Object> list = new ArrayList<>();
            for (Object member : members) {
                Object node = jsonNode(member);
                if (node == NOT_JSON) {
                    return NOT_JSON;
                }
                list.add(node);
            }
            return list;
        }
        if (value instanceof BMap<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object key : map.getKeys()) {
                Object node = jsonNode(map.get(key));
                if (node == NOT_JSON) {
                    return NOT_JSON;
                }
                result.put(key.toString(), node);
            }
            return result;
        }
        return NOT_JSON;
    }

    private static List<Object> arrayMembers(BArray array) {
        List<Object> members = new ArrayList<>();
        for (long i = 0; i < array.size(); i++) {
            members.add(array.get(i));
        }
        return members;
    }

    /**
     * Converts a Ballerina runtime value (anydata) to a schema tree node.
     */
    static Object toNode(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BString str) {
            return str.getValue();
        }
        if (value instanceof Long || value instanceof Double || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer i) {
            // Small int literals of finite types (e.g. `1|2`) are stored as integers.
            return i.longValue();
        }
        if (value instanceof BDecimal decimal) {
            return decimal.decimalValue();
        }
        if (value instanceof BArray array) {
            List<Object> list = new ArrayList<>();
            for (long i = 0; i < array.size(); i++) {
                list.add(toNode(array.get(i)));
            }
            return list;
        }
        if (value instanceof BMap<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Object key : map.getKeys()) {
                result.put(key.toString(), toNode(map.get(key)));
            }
            return result;
        }
        // e.g. `string:RegExp` values of `@constraint:String {pattern: ...}`
        return StringUtils.getStringValue(value);
    }
}
