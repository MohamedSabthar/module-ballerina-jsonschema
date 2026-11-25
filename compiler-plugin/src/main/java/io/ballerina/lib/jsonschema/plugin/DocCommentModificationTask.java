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
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleId;
import io.ballerina.projects.Package;
import io.ballerina.projects.plugins.ModifierTask;
import io.ballerina.projects.plugins.SourceModifierContext;
import io.ballerina.tools.text.TextDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Source modifier task that adds {@code @jsonschema:Description} annotations generated from doc comments to every
 * document that imports {@code ballerina/jsonschema} (including {@code import ballerina/jsonschema as _;}).
 *
 * @since 0.1.0
 */
public class DocCommentModificationTask implements ModifierTask<SourceModifierContext> {

    @Override
    public void modify(SourceModifierContext modifierContext) {
        Package currentPackage = modifierContext.currentPackage();
        if (modifierContext.compilation().diagnosticResult().errorCount() > 0) {
            return;
        }
        for (ModuleId moduleId : currentPackage.moduleIds()) {
            Module module = currentPackage.module(moduleId);
            for (DocumentId documentId : module.documentIds()) {
                TextDocument modified = modifyDocument(module.document(documentId));
                if (modified != null) {
                    modifierContext.modifySourceFile(modified, documentId);
                }
            }
            for (DocumentId documentId : module.testDocumentIds()) {
                TextDocument modified = modifyDocument(module.document(documentId));
                if (modified != null) {
                    modifierContext.modifyTestSourceFile(modified, documentId);
                }
            }
        }
    }

    /**
     * Returns the modified text document, or {@code null} if the document doesn't need to be modified.
     */
    static TextDocument modifyDocument(Document document) {
        ModulePartNode modulePartNode = document.syntaxTree().rootNode();
        Optional<ImportDeclarationNode> jsonSchemaImport = Utils.findJsonSchemaImport(modulePartNode.imports());
        if (jsonSchemaImport.isEmpty()) {
            return null;
        }
        String prefix = Utils.importPrefix(jsonSchemaImport.get());
        // A module that only declares types imports the library with `as _` (a named import would be unused). The
        // generated annotations need a prefix, so the import gets one.
        boolean unnamedImport = Utils.UNUSED_PREFIX.equals(prefix);
        if (unnamedImport) {
            prefix = Utils.availablePrefix(modulePartNode.imports());
        }
        SemanticModel semanticModel = document.module().getCompilation().getSemanticModel();
        DocCommentModifier modifier = new DocCommentModifier(prefix, semanticModel, document);
        ModulePartNode newRoot = (ModulePartNode) modulePartNode.apply(modifier);
        if (!modifier.isModified()) {
            // Nothing added, so an `as _` import stays as it is (a named prefix would be unused).
            return null;
        }
        if (unnamedImport) {
            newRoot = newRoot.modify().withImports(renameImport(newRoot.imports(), prefix)).apply();
        }
        return document.syntaxTree().modifyWith(newRoot).textDocument();
    }

    private static NodeList<ImportDeclarationNode> renameImport(NodeList<ImportDeclarationNode> imports,
                                                                String prefix) {
        // The `ballerina/jsonschema` import is always present here.
        ImportDeclarationNode jsonSchemaImport = Utils.findJsonSchemaImport(imports).orElseThrow();
        List<ImportDeclarationNode> renamed = new ArrayList<>();
        for (ImportDeclarationNode importDeclaration : imports) {
            renamed.add(importDeclaration == jsonSchemaImport
                    ? Utils.withPrefix(importDeclaration, prefix) : importDeclaration);
        }
        return NodeFactory.createNodeList(renamed);
    }
}
