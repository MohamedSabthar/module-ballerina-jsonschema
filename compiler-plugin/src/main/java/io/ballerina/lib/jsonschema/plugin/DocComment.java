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

package io.ballerina.lib.jsonschema.plugin;

import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed Ballerina doc comment: the description and the {@code # + name - text} parameter lines.
 *
 * @param description the description text (empty if there's none)
 * @param parameters  parameter (record field) descriptions keyed by name
 * @since 0.1.0
 */
record DocComment(String description, Map<String, String> parameters) {
    private static final DocComment EMPTY = new DocComment("", Map.of());
    private static final Pattern PARAMETER_LINE = Pattern.compile("^\\+\\s+(\\S+)\\s+-\\s?(.*)$");
    private static final Pattern RETURN_LINE = Pattern.compile("^\\+\\s+return\\s+-.*$");
    private static final Pattern DEPRECATION_LINE = Pattern.compile("^#\\s*Deprecated\\b.*$");
    private static final String CODE_FENCE = "```";

    static DocComment parse(Optional<MetadataNode> metadata) {
        if (metadata.isEmpty()) {
            return EMPTY;
        }
        Optional<Node> documentation = metadata.get().documentationString();
        return documentation.map(node -> parse(node.toSourceCode())).orElse(EMPTY);
    }

    /**
     * Parses the source text of a doc comment (lines starting with {@code #}).
     */
    static DocComment parse(String source) {
        List<String> descriptionLines = new ArrayList<>();
        Map<String, List<String>> parameterLines = new LinkedHashMap<>();
        List<String> current = descriptionLines;
        for (String rawLine : source.split("\\R")) {
            String line = rawLine.strip();
            if (!line.startsWith("#")) {
                continue;
            }
            line = line.substring(1);
            if (line.startsWith(" ")) {
                line = line.substring(1);
            }
            String trimmed = line.strip();
            if (DEPRECATION_LINE.matcher(trimmed).matches() || RETURN_LINE.matcher(trimmed).matches()) {
                current = null;
                continue;
            }
            Matcher parameter = PARAMETER_LINE.matcher(trimmed);
            if (parameter.matches()) {
                current = new ArrayList<>();
                current.add(parameter.group(2));
                parameterLines.put(unescapeIdentifier(parameter.group(1)), current);
                continue;
            }
            if (current != null) {
                current.add(current == descriptionLines ? line : trimmed);
            }
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameterLines.forEach((name, lines) -> {
            String text = join(lines);
            if (!text.isEmpty()) {
                parameters.put(name, text);
            }
        });
        return new DocComment(join(descriptionLines), parameters);
    }

    /**
     * Joins doc lines into a single text: consecutive lines are joined with a space, blank lines become paragraph
     * breaks and fenced code blocks keep their line breaks.
     */
    static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        boolean inCode = false;
        boolean paragraphBreak = false;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith(CODE_FENCE)) {
                appendSeparator(sb, paragraphBreak ? "\n\n" : "\n");
                sb.append(trimmed);
                inCode = !inCode;
                paragraphBreak = false;
                continue;
            }
            if (inCode) {
                sb.append('\n').append(line);
                continue;
            }
            if (trimmed.isEmpty()) {
                paragraphBreak = true;
                continue;
            }
            if (sb.isEmpty()) {
                sb.append(trimmed);
            } else if (paragraphBreak || sb.toString().endsWith(CODE_FENCE)) {
                sb.append(paragraphBreak ? "\n\n" : "\n").append(trimmed);
            } else {
                sb.append(' ').append(trimmed);
            }
            paragraphBreak = false;
        }
        return sb.toString().strip();
    }

    private static void appendSeparator(StringBuilder sb, String separator) {
        if (!sb.isEmpty()) {
            sb.append(separator);
        }
    }

    static String unescapeIdentifier(String name) {
        return name.startsWith("'") ? name.substring(1) : name;
    }
}
