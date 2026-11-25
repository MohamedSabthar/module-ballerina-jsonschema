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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Unit tests for doc comment parsing and string literal escaping.
 */
public class DocCommentTest {

    @Test
    public void testParseDescription() {
        DocComment doc = DocComment.parse("""
                # First line
                #continues here
                #
                #
                # Second paragraph.
                not a doc line
                """);
        Assert.assertEquals(doc.description(), "First line continues here\n\nSecond paragraph.");
        Assert.assertEquals(doc.parameters(), Map.of());
    }

    @Test
    public void testParseParametersAndSections() {
        DocComment doc = DocComment.parse("""
                # Description
                # + a - First
                #   continued
                # + 'b - Second
                # + c -
                # + return - Return value
                # more return text
                # # Deprecated
                # Deprecated text
                """);
        Assert.assertEquals(doc.description(), "Description");
        Assert.assertEquals(doc.parameters(), Map.of("a", "First continued", "b", "Second"));
    }

    @Test
    public void testCodeBlocks() {
        DocComment doc = DocComment.parse("""
                # ```
                # code
                #   indented
                # ```
                # Text
                """);
        Assert.assertEquals(doc.description(), "```\ncode\n  indented\n```\nText");
    }

    @Test
    public void testParagraphBeforeAndBlankLinesInsideCodeBlock() {
        DocComment doc = DocComment.parse("""
                # Intro
                #
                # ```
                # first
                #
                # second
                # ```
                #
                # Outro
                """);
        Assert.assertEquals(doc.description(), "Intro\n\n```\nfirst\n\nsecond\n```\n\nOutro");
    }

    @Test
    public void testUnclosedCodeBlock() {
        DocComment doc = DocComment.parse("""
                # Text
                # ```
                # code
                """);
        Assert.assertEquals(doc.description(), "Text\n```\ncode");
    }

    @Test
    public void testWindowsLineEndingsAndIndentation() {
        DocComment doc = DocComment.parse("    # First\r\n    # second\r\n    # + a - Field a\r\n");
        Assert.assertEquals(doc.description(), "First second");
        Assert.assertEquals(doc.parameters(), Map.of("a", "Field a"));
    }

    @Test
    public void testParameterLineVariants() {
        DocComment doc = DocComment.parse("""
                # +   spaced   -   Extra spaces
                # + tight -No space after dash
                # +nospace - Not a parameter line
                # + dash-less Not a parameter line either
                """);
        Assert.assertEquals(doc.parameters(),
                // Lines that aren't parameter lines continue the previous parameter.
                Map.of("spaced", "Extra spaces", "tight",
                        "No space after dash +nospace - Not a parameter line + dash-less Not a parameter line either"));
        Assert.assertEquals(doc.description(), "");
    }

    @Test
    public void testTrailingWhitespaceAndOnlyHashes() {
        DocComment doc = DocComment.parse("#\n#   \n# Text   \n#\n");
        Assert.assertEquals(doc.description(), "Text");
    }

    @Test
    public void testDeprecatedSectionStopsParameters() {
        DocComment doc = DocComment.parse("""
                # + a - Field a
                # # Deprecated
                # + b - Field b is after the deprecation section, so it's still a parameter
                """);
        Assert.assertEquals(doc.parameters().get("a"), "Field a");
        Assert.assertTrue(doc.parameters().containsKey("b"));
    }

    @Test
    public void testEmpty() {
        Assert.assertEquals(DocComment.parse("").description(), "");
    }

    @Test
    public void testToStringLiteral() {
        Assert.assertEquals(Utils.toStringLiteral("a\"b\\c\nd\re\tf\u0001g\u007fh😀"),
                "\"a\\\"b\\\\c\\nd\\re\\tf\\u{1}g\\u{7F}h😀\"");
    }
}
