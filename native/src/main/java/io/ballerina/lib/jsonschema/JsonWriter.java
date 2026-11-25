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

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON serializer for the schema tree built by {@link SchemaGenerator}.
 * Supported node types: {@link Map}, {@link List}, {@link String}, {@link Long}, {@link Double},
 * {@link BigDecimal}, {@link Boolean} and {@code null}.
 *
 * @since 0.1.0
 */
public final class JsonWriter {
    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    public static String write(Object node, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        write(sb, node, pretty, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object node, boolean pretty, int depth) {
        if (node == null) {
            sb.append("null");
        } else if (node instanceof String str) {
            writeString(sb, str);
        } else if (node instanceof Boolean || node instanceof Long) {
            sb.append(node);
        } else if (node instanceof Double d) {
            if (d.isNaN() || d.isInfinite()) {
                sb.append("null");
            } else {
                sb.append(d);
            }
        } else if (node instanceof BigDecimal bd) {
            sb.append(bd.toString());
        } else if (node instanceof Map<?, ?> map) {
            writeMap(sb, map, pretty, depth);
        } else {
            writeList(sb, (List<?>) node, pretty, depth);
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, boolean pretty, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = it.next();
            newLine(sb, pretty, depth + 1);
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(pretty ? ": " : ":");
            write(sb, entry.getValue(), pretty, depth + 1);
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        newLine(sb, pretty, depth);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list, boolean pretty, int depth) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            newLine(sb, pretty, depth + 1);
            write(sb, list.get(i), pretty, depth + 1);
            if (i < list.size() - 1) {
                sb.append(',');
            }
        }
        newLine(sb, pretty, depth);
        sb.append(']');
    }

    private static void newLine(StringBuilder sb, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        sb.append('\n');
        sb.append(INDENT.repeat(depth));
    }

    static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
