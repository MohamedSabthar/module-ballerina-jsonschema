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
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.RecordFieldNode;
import io.ballerina.compiler.syntax.tree.RecordFieldWithDefaultValueNode;
import io.ballerina.compiler.syntax.tree.RecordTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TreeModifier;
import io.ballerina.compiler.syntax.tree.TypeDefinitionNode;
import io.ballerina.projects.Document;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

/**
 * Adds {@code @<prefix>:Description {value: "..."}} annotations to type definitions and record fields that have
 * doc comments. Field descriptions come from the field's own doc comment, or else from the {@code # + field - ...}
 * lines of the enclosing type definition's doc comment.
 * <p>
 * Also adds {@code @<prefix>:Default {value: <expr>}} to record fields whose default value expression is
 * side-effect free (see {@link SafeDefaultChecker}).
 *
 * @since 0.1.0
 */
class DocCommentModifier extends TreeModifier {
    static final String DESCRIPTION_ANNOTATION = "Description";
    static final String DEFAULT_ANNOTATION = "Default";

    private final String prefix;
    private final SafeDefaultChecker safeDefaults;
    private final Deque<Map<String, String>> fieldDocsStack = new ArrayDeque<>();
    /** Parameter docs ({@code # + field - ...}) of the type definition being visited. */
    private Map<String, String> typeFieldDocs = Map.of();
    /** Number of record fields being visited (0 when visiting the top level of a type definition). */
    private int fieldDepth = 0;
    private boolean modified = false;

    DocCommentModifier(String prefix, SemanticModel semanticModel, Document document) {
        this.prefix = prefix;
        this.safeDefaults = new SafeDefaultChecker(semanticModel, document);
    }

    boolean isModified() {
        return modified;
    }

    @Override
    public TypeDefinitionNode transform(TypeDefinitionNode node) {
        DocComment doc = DocComment.parse(node.metadata());
        typeFieldDocs = doc.parameters();
        TypeDefinitionNode updated = super.transform(node);
        typeFieldDocs = Map.of();
        if (doc.description().isEmpty()) {
            return updated;
        }
        Optional<MetadataNode> metadata = withDescription(updated.metadata(), doc.description());
        return metadata.map(m -> updated.modify().withMetadata(m).apply()).orElse(updated);
    }

    @Override
    public RecordTypeDescriptorNode transform(RecordTypeDescriptorNode node) {
        // Parameter docs of a type definition apply to the fields of its top-level records (including each member of
        // a union or intersection), but not to records nested in field types.
        fieldDocsStack.push(fieldDepth == 0 ? typeFieldDocs : Map.of());
        try {
            return super.transform(node);
        } finally {
            fieldDocsStack.pop();
        }
    }

    @Override
    public RecordFieldNode transform(RecordFieldNode node) {
        fieldDepth++;
        RecordFieldNode updated = super.transform(node);
        fieldDepth--;
        String description = fieldDescription(updated.metadata(), updated.fieldName().text());
        if (description.isEmpty()) {
            return updated;
        }
        Optional<MetadataNode> metadata = withDescription(updated.metadata(), description);
        return metadata.map(m -> updated.modify().withMetadata(m).apply()).orElse(updated);
    }

    @Override
    public RecordFieldWithDefaultValueNode transform(RecordFieldWithDefaultValueNode node) {
        // The semantic model works on the original (unmodified) nodes.
        boolean safeDefault = safeDefaults.isSafe(node.expression());
        fieldDepth++;
        RecordFieldWithDefaultValueNode updated = super.transform(node);
        fieldDepth--;
        MetadataNode metadata = updated.metadata().orElse(null);
        String description = fieldDescription(updated.metadata(), updated.fieldName().text());
        if (!description.isEmpty()) {
            metadata = withDescription(Optional.ofNullable(metadata), description).orElse(metadata);
        }
        if (safeDefault) {
            // A newline before `}` keeps a trailing line comment of the expression from swallowing it.
            String value = node.expression().toSourceCode().strip() + System.lineSeparator();
            metadata = withAnnotation(Optional.ofNullable(metadata), DEFAULT_ANNOTATION, value).orElse(metadata);
        }
        return metadata == updated.metadata().orElse(null) ? updated
                : updated.modify().withMetadata(metadata).apply();
    }

    private String fieldDescription(Optional<MetadataNode> metadata, String fieldName) {
        String own = DocComment.parse(metadata).description();
        if (!own.isEmpty()) {
            return own;
        }
        // Fields are always visited within a record type descriptor, so the stack is never empty here.
        return fieldDocsStack.peek().getOrDefault(DocComment.unescapeIdentifier(fieldName), "");
    }

    private Optional<MetadataNode> withDescription(Optional<MetadataNode> metadata, String description) {
        return withAnnotation(metadata, DESCRIPTION_ANNOTATION, Utils.toStringLiteral(description));
    }

    /**
     * Returns the metadata with a {@code @<prefix>:<name> {value: <value>}} annotation appended, or empty if the
     * metadata already has that annotation.
     */
    private Optional<MetadataNode> withAnnotation(Optional<MetadataNode> metadata, String name, String value) {
        if (metadata.isPresent() && hasAnnotation(metadata.get().annotations(), name)) {
            return Optional.empty();
        }
        AnnotationNode annotation = NodeParser.parseAnnotation(String.format("@%s:%s {value: %s}%n", prefix,
                name, value));
        modified = true;
        if (metadata.isEmpty()) {
            return Optional.of(NodeFactory.createMetadataNode(null, NodeFactory.createNodeList(annotation)));
        }
        MetadataNode existing = metadata.get();
        return Optional.of(existing.modify().withAnnotations(existing.annotations().add(annotation)).apply());
    }

    private boolean hasAnnotation(NodeList<AnnotationNode> annotations, String name) {
        for (AnnotationNode annotation : annotations) {
            if (annotation.annotReference() instanceof QualifiedNameReferenceNode qualifiedName
                    && prefix.equals(qualifiedName.modulePrefix().text())
                    && name.equals(qualifiedName.identifier().text())) {
                return true;
            }
        }
        return false;
    }
}
