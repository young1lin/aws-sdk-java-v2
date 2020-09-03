/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.codegen.jmespath;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import org.junit.Test;
import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.component.IndexExpression;
import software.amazon.awssdk.codegen.jmespath.component.StarExpression;

public class JmesPathParserTest extends TestCase {
    @Test
    public void testeSubExpressionWithIdentifier() {
        Expression expression = JmesPathParser.parse("a.b");
        assertThat(expression.asSubExpression().leftExpression().asIdentifier()).isEqualTo("a");
        assertThat(expression.asSubExpression().rightSubExpression().asIdentifier()).isEqualTo("b");
    }

    @Test
    public void testSubExpressionWithMultiSelectList() {
        Expression expression = JmesPathParser.parse("a.b[c, d]");
        assertThat(expression.asSubExpression().leftExpression().asIdentifier()).isEqualTo("a");
        assertThat(expression.asSubExpression().rightSubExpression().asMultiSelectList().expressions().get(0).asIdentifier()).isEqualTo("c");
        assertThat(expression.asSubExpression().rightSubExpression().asMultiSelectList().expressions().get(1).asIdentifier()).isEqualTo("d");
    }

    @Test
    public void testStarExpression() {
        Expression expression = JmesPathParser.parse("*");
        assertThat(expression.isStarExpression()).isTrue();
        assertThat(expression.asStarExpression()).isInstanceOf(StarExpression.class);
    }

    @Test
    public void testEmptyIndexedExpression() {
        Expression expression = JmesPathParser.parse("[]");
        assertThat(expression.isIndexExpression()).isTrue();
        assertThat(expression.asIndexExpression().expression()).isNotPresent();
        assertThat(expression.asIndexExpression().bracketSpecifier().isBracketSpecifierWithoutContents()).isTrue();
    }

    @Test
    public void testIndexedExpressionWithContents() {
        Expression expression = JmesPathParser.parse("[0]");
        assertThat(expression.isIndexExpression()).isTrue();
        assertThat(expression.asIndexExpression().expression()).isNotPresent();
        assertThat(expression.asIndexExpression().bracketSpecifier().isBracketSpecifierWithContents()).isTrue();
        assertThat(expression.asIndexExpression().bracketSpecifier().asBracketSpecifierWithContents().isInteger()).isTrue();
        assertThat(expression.asIndexExpression().bracketSpecifier().asBracketSpecifierWithContents().asInteger()).isEqualTo(0);
    }

    @Test
    public void testIndexedExpressionWithQuestionMark() {
        Expression expression = JmesPathParser.parse("[?a]");
        assertThat(expression.isIndexExpression()).isTrue();

        IndexExpression indexExpression = expression.asIndexExpression();
        assertThat(indexExpression.expression()).isNotPresent();
        assertThat(indexExpression.bracketSpecifier().isBracketSpecifierWithQuestionMark()).isTrue();

        Expression bracketedExpression = indexExpression.bracketSpecifier().asBracketSpecifierWithQuestionMark().expression();
        assertThat(bracketedExpression.isIdentifier()).isTrue();
        assertThat(bracketedExpression.asIdentifier()).isEqualTo("a");
    }
}