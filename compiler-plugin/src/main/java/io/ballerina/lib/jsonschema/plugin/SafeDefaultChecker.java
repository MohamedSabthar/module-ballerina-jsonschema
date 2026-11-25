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

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.VariableSymbol;
import io.ballerina.compiler.syntax.tree.BracedExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ListConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.UnaryExpressionNode;
import io.ballerina.projects.Document;
import io.ballerina.tools.text.LinePosition;

import java.util.List;
import java.util.Optional;

/**
 * Decides whether a record field's default value expression can be copied into a {@code @jsonschema:Default}
 * annotation: it must have no side effects, always evaluate to the same value and never panic.
 * <p>
 * Allowed: literals ({@code 1}, {@code "a"}, {@code true}, {@code ()}, {@code null}), negated numeric literals,
 * references to constants, enum members and module-level variables (which must be {@code final} or
 * {@code configurable} to be used in a default value), and
 * parenthesized expressions and list/mapping constructors made of those. Anything else (function and method calls,
 * operators, templates, casts, spreads, computed keys, ...) is not.
 *
 * @since 0.1.0
 */
class SafeDefaultChecker {
    private final SemanticModel semanticModel;
    private final Document document;
    private List<Symbol> moduleLevelSymbols;

    SafeDefaultChecker(SemanticModel semanticModel, Document document) {
        this.semanticModel = semanticModel;
        this.document = document;
    }

    boolean isSafe(ExpressionNode expression) {
        return switch (expression.kind()) {
            case NUMERIC_LITERAL, STRING_LITERAL, BOOLEAN_LITERAL, NIL_LITERAL, NULL_LITERAL -> true;
            case UNARY_EXPRESSION -> {
                UnaryExpressionNode unary = (UnaryExpressionNode) expression;
                yield unary.unaryOperator().kind() == SyntaxKind.MINUS_TOKEN
                        && unary.expression().kind() == SyntaxKind.NUMERIC_LITERAL;
            }
            case BRACED_EXPRESSION -> isSafe(((BracedExpressionNode) expression).expression());
            case SIMPLE_NAME_REFERENCE, QUALIFIED_NAME_REFERENCE -> isSafeReference(expression);
            case LIST_CONSTRUCTOR -> {
                for (Node member : ((ListConstructorExpressionNode) expression).expressions()) {
                    // Spread members (`...xs`) are not expressions.
                    if (!(member instanceof ExpressionNode memberExpression) || !isSafe(memberExpression)) {
                        yield false;
                    }
                }
                yield true;
            }
            case MAPPING_CONSTRUCTOR -> {
                for (MappingFieldNode field : ((MappingConstructorExpressionNode) expression).fields()) {
                    if (!isSafeField(field)) {
                        yield false;
                    }
                }
                yield true;
            }
            default -> false;
        };
    }

    /**
     * A {@code key: value} field with a literal or identifier key, or a {@code {name}} shorthand field.
     * Spread fields and computed keys are not allowed.
     */
    private boolean isSafeField(MappingFieldNode field) {
        // Spread fields (`...m`) and computed keys (`[k]: v`) are other node kinds.
        if (!(field instanceof SpecificFieldNode specificField)) {
            return false;
        }
        Optional<ExpressionNode> value = specificField.valueExpr();
        return value.isPresent() ? isSafe(value.get()) : isSafeReference(specificField.fieldName());
    }

    private boolean isSafeReference(Node reference) {
        return semanticModel.symbol(reference).map(symbol -> isSafeSymbol(symbol, reference)).orElse(false);
    }

    private boolean isSafeSymbol(Symbol symbol, Node reference) {
        if (symbol.kind() == SymbolKind.CONSTANT || symbol.kind() == SymbolKind.ENUM_MEMBER) {
            return true;
        }
        // The compiler only allows immutable storage in default values, so a referenced variable is `final` or
        // `configurable` (with an immutable value). Only module-level ones are allowed: a local variable isn't in
        // scope of the annotation. Variables of other modules (`mod:x`) are always module-level.
        return symbol instanceof VariableSymbol variable
                && (reference.kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE || isModuleLevel(variable));
    }

    private boolean isModuleLevel(Symbol symbol) {
        if (moduleLevelSymbols == null) {
            // The symbols visible at the start of the document are the module-level ones (including those of test
            // sources when the document is a test document, unlike `moduleSymbols()`).
            moduleLevelSymbols = semanticModel.visibleSymbols(document, LinePosition.from(0, 0));
        }
        return moduleLevelSymbols.contains(symbol);
    }
}
