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

import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportOrgNameNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.Token;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Utility functions for the compiler plugin.
 *
 * @since 0.1.0
 */
final class Utils {
    static final String BALLERINA_ORG_NAME = "ballerina";
    static final String JSONSCHEMA_MODULE_NAME = "jsonschema";
    /** The prefix of an import that is only needed for its side effects (here, running the compiler plugin). */
    static final String UNUSED_PREFIX = "_";

    private Utils() {
    }

    /**
     * Returns the {@code import ballerina/jsonschema} declaration in the given imports, if any.
     */
    static Optional<ImportDeclarationNode> findJsonSchemaImport(NodeList<ImportDeclarationNode> imports) {
        for (ImportDeclarationNode importDeclaration : imports) {
            Optional<ImportOrgNameNode> orgName = importDeclaration.orgName();
            if (orgName.isEmpty() || !BALLERINA_ORG_NAME.equals(orgName.get().orgName().text())) {
                continue;
            }
            SeparatedNodeList<IdentifierToken> moduleName = importDeclaration.moduleName();
            if (moduleName.size() == 1 && JSONSCHEMA_MODULE_NAME.equals(moduleName.get(0).text())) {
                return Optional.of(importDeclaration);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the prefix an import declaration binds: the explicit prefix, or else the last part of the module name.
     */
    static String importPrefix(ImportDeclarationNode importDeclaration) {
        Optional<ImportPrefixNode> prefix = importDeclaration.prefix();
        if (prefix.isPresent()) {
            return prefix.get().prefix().text();
        }
        SeparatedNodeList<IdentifierToken> moduleName = importDeclaration.moduleName();
        return moduleName.get(moduleName.size() - 1).text();
    }

    /**
     * Returns a module prefix that isn't bound by any of the given imports: {@code jsonschema}, or else
     * {@code jsonschema1}, {@code jsonschema2}, ...
     */
    static String availablePrefix(NodeList<ImportDeclarationNode> imports) {
        Set<String> used = new HashSet<>();
        for (ImportDeclarationNode importDeclaration : imports) {
            used.add(importPrefix(importDeclaration));
        }
        String prefix = JSONSCHEMA_MODULE_NAME;
        for (int i = 1; used.contains(prefix); i++) {
            prefix = JSONSCHEMA_MODULE_NAME + i;
        }
        return prefix;
    }

    /**
     * Returns the import declaration with its prefix ({@code as _}) replaced by the given prefix.
     */
    static ImportDeclarationNode withPrefix(ImportDeclarationNode importDeclaration, String prefix) {
        // Only called for `as _` imports, so the prefix node is always present.
        ImportPrefixNode prefixNode = importDeclaration.prefix().orElseThrow();
        Token oldPrefix = prefixNode.prefix();
        IdentifierToken newPrefix = NodeFactory.createIdentifierToken(prefix, oldPrefix.leadingMinutiae(),
                oldPrefix.trailingMinutiae());
        return importDeclaration.modify().withPrefix(prefixNode.modify().withPrefix(newPrefix).apply()).apply();
    }

    /**
     * Converts the given text to a Ballerina string literal (including the surrounding double quotes).
     */
    static String toStringLiteral(String text) {
        StringBuilder sb = new StringBuilder("\"");
        text.codePoints().forEach(cp -> {
            switch (cp) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (cp < 0x20 || cp == 0x7f) {
                        sb.append(String.format("\\u{%X}", cp));
                    } else {
                        sb.appendCodePoint(cp);
                    }
                }
            }
        });
        return sb.append('"').toString();
    }
}
