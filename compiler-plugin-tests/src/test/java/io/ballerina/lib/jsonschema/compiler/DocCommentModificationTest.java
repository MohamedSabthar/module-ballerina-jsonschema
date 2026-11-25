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

package io.ballerina.lib.jsonschema.compiler;

import io.ballerina.projects.DiagnosticResult;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.projects.environment.Environment;
import io.ballerina.projects.environment.EnvironmentBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Tests that the compiler plugin adds {@code @jsonschema:Description} annotations generated from doc comments.
 */
public class DocCommentModificationTest {

    private static final Path RESOURCE_DIRECTORY = Paths.get("src", "test", "resources",
            "ballerina_sources", "doc_comment_tests").toAbsolutePath();
    private static final Path DISTRIBUTION_PATH = Paths.get(System.getProperty("ballerina.home",
            Paths.get("../", "target", "ballerina-runtime").toString())).toAbsolutePath();

    @Test
    public void testTypeDescriptions() {
        String source = modifiedSource("01_doc_comments", "main.bal");
        Assert.assertTrue(source.contains("@js:Description {value: \"A user.\\n\\nSecond paragraph with `code`.\\n"
                + "```\\nint x = 1;\\n```\\nAfter code.\"}"), source);
        Assert.assertTrue(source.contains(
                "@js:Description {value: \"Escaped \\\"quotes\\\", \\\\ backslash and\\ttab.\"}"), source);
    }

    @Test
    public void testFieldDescriptions() {
        String source = modifiedSource("01_doc_comments", "main.bal");
        // Field doc comments
        Assert.assertTrue(source.replaceAll("\\s+", " ")
                .contains("@display {label: \"Name\"} @js:Description {value: \"Display name\"} string name;"), source);
        Assert.assertTrue(source.contains("@js:Description {value: \"Nested record\"}"), source);
        Assert.assertTrue(source.contains("@js:Description {value: \"Inner field\"}"), source);
        Assert.assertTrue(source.contains("@js:Description {value: \"Field with default\"}"), source);
        // `# + field - ...` lines of the type definition, including continuation lines and quoted identifiers
        Assert.assertTrue(source.contains("@js:Description {value: \"Contact email address\"}"), source);
        Assert.assertTrue(source.contains("@js:Description {value: \"The kind\"}"), source);
    }

    @Test
    public void testParameterDocsForUnionsIntersectionsAndNestedRecords() {
        String source = modifiedSource("01_doc_comments", "main.bal").replaceAll("\\s+", " ");
        // Both members of the union get the `# + id - ...` description...
        Assert.assertTrue(source.contains("{| @js:Description {value: \"Identifier\"} int id; # Nested"), source);
        Assert.assertTrue(source.contains("{| @js:Description {value: \"Identifier\"} string id; |}"), source);
        // ...but a record nested in a field type does not.
        Assert.assertTrue(source.contains("record {| int id; |} inner;"), source);
        Assert.assertTrue(source.contains("{| @js:Description {value: \"Read-only identifier\"} int id; |}"), source);
    }

    @Test
    public void testExistingDescriptionsAreKept() {
        String source = modifiedSource("01_doc_comments", "main.bal");
        Assert.assertFalse(source.contains("Ignored doc\"}"), source);
        Assert.assertFalse(source.contains("{value: \"Already described type.\"}"), source);
        Assert.assertEquals(count(source, "@js:Description"), 15, source);
    }

    @Test
    public void testDocumentsWithoutChanges() {
        Assert.assertFalse(modifiedSource("01_doc_comments", "no_docs.bal").contains("Description"));
        String source = modifiedSource("01_doc_comments", "underscore_no_docs.bal");
        Assert.assertFalse(source.contains("Description"), source);
        // Without annotations to add, the `_` prefix is kept (a named prefix would be an unused import).
        Assert.assertTrue(source.contains("import ballerina/jsonschema as _;"), source);
    }

    @Test
    public void testUnderscoreImportGetsAPrefix() {
        String source = modifiedSource("01_doc_comments", "underscore.bal");
        Assert.assertTrue(source.startsWith("import ballerina/jsonschema as jsonschema;\n"), source);
        Assert.assertTrue(source.contains("@jsonschema:Description {value: \"A type in a file that imports the library "
                + "only for its compiler plugin.\"}"), source);
        Assert.assertTrue(source.contains("@jsonschema:Description {value: \"The x value\"}"), source);
        Assert.assertTrue(source.contains("@jsonschema:Description {value: \"The y value\"}"), source);
    }

    @Test
    public void testUnderscoreImportWithTakenPrefix() {
        String source = modifiedSource("01_doc_comments", "underscore_prefix_taken.bal");
        Assert.assertTrue(source.contains("import ballerina/jsonschema as jsonschema1;"), source);
        Assert.assertTrue(source.contains("import ballerina/io as jsonschema;"), source);
        Assert.assertTrue(source.contains(
                "@jsonschema1:Description {value: \"The `jsonschema` prefix is taken in this file.\"}"), source);
    }

    @Test
    public void testSafeDefaultsGetDefaultAnnotations() {
        String source = modifiedSource("01_doc_comments", "defaults.bal").replaceAll("\\s+", " ");
        String[][] expected = {
                {"1", "int intLiteral"}, {"\"s\"", "string stringLiteral"}, {"true", "boolean booleanLiteral"},
                {"()", "int? nilLiteral"}, {"null", "json nullLiteral"}, {"-1.5", "decimal negativeLiteral"},
                {"(2)", "int braced"}, {"MAX", "int constant"}, {"region", "string finalVariable"},
                {"timeout", "int configurableVariable"}, {"RED", "Color enumMember"},
                {"util:REMOTE_MAX", "int otherModuleConstant"}, {"util:remoteRegion", "string otherModuleFinal"},
                {"[1, MAX, -2]", "int[] list"},
                {"{a: 1, \"b\": region, c: [MAX, RED], timeout}", "map<anydata> mapping"},
                {"4", "int trailingComment"}
        };
        for (String[] field : expected) {
            String annotation = "@js:Default {value: " + field[0] + " } " + field[1] + " = ";
            Assert.assertTrue(source.contains(annotation), annotation + " not found in " + source);
        }
        // A trailing line comment of the expression doesn't swallow the closing brace.
        Assert.assertTrue(source.contains("@js:Default {value: 4 } int trailingComment = 4; // a comment"), source);
        // An existing annotation is kept.
        Assert.assertTrue(source.contains("@js:Default {value: 99} int existingDefault = 5;"), source);
        // Description and default together.
        Assert.assertTrue(source.contains("@js:Description {value: \"Documented and defaulted\"} "
                + "@js:Default {value: 6 } int documented"), source);
        for (String unsafe : new String[]{"functionCall", "binary", "template", "unaryPlus", "negatedReference",
                "listSpread", "mappingSpread", "computedKey", "typeCast", "unsafeMember",
                "unsafeValue", "typeReference", "fromLocal"}) {
            Assert.assertTrue(source.matches(".*[;|] [^;@]+ " + unsafe + " = .*"), unsafe + " must not have a default: "
                    + source);
        }
        // A local record type gets literal defaults, but not references to local variables.
        Assert.assertTrue(source.contains("record {| int fromLocal = localFinal; @js:Default {value: 8 } int literal"),
                source);
        // 16 safe defaults, the existing one, the documented one and the local literal.
        Assert.assertEquals(count(source, "@js:Default"), 19, source);
    }

    @Test
    public void testUnderscoreImportWithDefaultsOnly() {
        String source = modifiedSource("01_doc_comments", "underscore_defaults.bal");
        Assert.assertTrue(source.contains("import ballerina/jsonschema as jsonschema;"), source);
        Assert.assertTrue(source.replaceAll("\\s+", " ").contains("@jsonschema:Default {value: 1 } int x = 1;"),
                source);
    }

    @Test
    public void testTestDocuments() {
        BuildProject project = loadProject("01_doc_comments");
        Module module = project.currentPackage().getDefaultModule();
        String typesSource = testSource(module, module.testDocumentIds(), "test_types.bal");
        Assert.assertTrue(typesSource.contains("@jsonschema:Description {value: \"A test type.\"}"), typesSource);
        Assert.assertTrue(typesSource.contains("@jsonschema:Description {value: \"A test field\"}"), typesSource);
        String noImportSource = testSource(module, module.testDocumentIds(), "no_import.bal");
        Assert.assertFalse(noImportSource.contains("Description"), noImportSource);
    }

    @Test
    public void testNoModificationOnCompilationErrors() {
        BuildProject project = BuildProject.load(getEnvironmentBuilder(),
                RESOURCE_DIRECTORY.resolve("02_compilation_error"));
        project.currentPackage().runCodeGenAndModifyPlugins();
        DiagnosticResult diagnosticResult = project.currentPackage().getCompilation().diagnosticResult();
        Assert.assertTrue(diagnosticResult.errorCount() > 0);
        Module module = project.currentPackage().getDefaultModule();
        String source = testSource(module, module.documentIds(), "main.bal");
        Assert.assertFalse(source.contains("Description"), source);
    }

    @Test
    public void testNoImport() {
        Assert.assertFalse(modifiedSource("03_no_import", "main.bal").contains("Description"));
    }

    private static String modifiedSource(String packageName, String fileName) {
        BuildProject project = loadProject(packageName);
        Module module = project.currentPackage().getDefaultModule();
        return testSource(module, module.documentIds(), fileName);
    }

    private static BuildProject loadProject(String packageName) {
        BuildProject project = BuildProject.load(getEnvironmentBuilder(), RESOURCE_DIRECTORY.resolve(packageName));
        DiagnosticResult diagnosticResult = project.currentPackage().runCodeGenAndModifyPlugins();
        Assert.assertEquals(diagnosticResult.errorCount(), 0, diagnosticResult.errors().toString());
        return project;
    }

    private static String testSource(Module module, Collection<DocumentId> documentIds, String fileName) {
        for (DocumentId documentId : documentIds) {
            Document document = module.document(documentId);
            String name = document.name().replace('\\', '/');
            if (name.equals(fileName) || name.endsWith("/" + fileName)) {
                return document.syntaxTree().toSourceCode();
            }
        }
        throw new AssertionError("document not found: " + fileName);
    }

    private static int count(String source, String text) {
        int count = 0;
        for (int i = source.indexOf(text); i >= 0; i = source.indexOf(text, i + 1)) {
            count++;
        }
        return count;
    }

    private static ProjectEnvironmentBuilder getEnvironmentBuilder() {
        Environment environment = EnvironmentBuilder.getBuilder().setBallerinaHome(DISTRIBUTION_PATH).build();
        return ProjectEnvironmentBuilder.getBuilder(environment);
    }
}
