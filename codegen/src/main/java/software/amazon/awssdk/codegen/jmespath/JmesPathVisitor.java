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

import com.fasterxml.jackson.jr.stree.JrsValue;
import software.amazon.awssdk.codegen.jmespath.component.AndExpression;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifier;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierContent;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithContents;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithQuestionMark;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithoutContents;
import software.amazon.awssdk.codegen.jmespath.component.Comparator;
import software.amazon.awssdk.codegen.jmespath.component.ComparatorExpression;
import software.amazon.awssdk.codegen.jmespath.component.CurrentNodeExpression;
import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.component.ExpressionType;
import software.amazon.awssdk.codegen.jmespath.component.FunctionArg;
import software.amazon.awssdk.codegen.jmespath.component.FunctionExpression;
import software.amazon.awssdk.codegen.jmespath.component.IndexExpression;
import software.amazon.awssdk.codegen.jmespath.component.KeyValueExpression;
import software.amazon.awssdk.codegen.jmespath.component.Literal;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectHash;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectList;
import software.amazon.awssdk.codegen.jmespath.component.NotExpression;
import software.amazon.awssdk.codegen.jmespath.component.OrExpression;
import software.amazon.awssdk.codegen.jmespath.component.ParenExpression;
import software.amazon.awssdk.codegen.jmespath.component.PipeExpression;
import software.amazon.awssdk.codegen.jmespath.component.SliceExpression;
import software.amazon.awssdk.codegen.jmespath.component.StarExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpression;

public interface JmesPathVisitor {
    default void visitExpression(Expression expression) {
    }

    default void visitSubExpression(SubExpression subExpression) {
    }

    default void visitPipeExpression(PipeExpression pipeExpression) {
    }

    default void visitOrExpression(OrExpression orExpression) {
    }

    default void visitAndExpression(AndExpression andExpression) {
    }

    default void visitNotExpression(NotExpression notExpression) {
    }

    default void visitParenExpression(ParenExpression parenExpression) {
    }

    default void visitIndexExpression(IndexExpression indexExpression) {
    }

    default void visitMultiSelectList(MultiSelectList multiSelectList) {
    }

    default void visitMultiSelectHash(MultiSelectHash multiSelectHash) {
    }

    default void visitKeyValueExpression(KeyValueExpression keyValueExpression) {
    }

    default void visitBracketSpecifier(BracketSpecifier bracketSpecifier) {
    }

    default void visitBracketSpecifierWithContents(BracketSpecifierWithContents bracketSpecifierWithContents) {
    }

    default void visitBracketSpecifierContents(BracketSpecifierContent bracketSpecifierContent) {
    }

    default void visitBracketSpecifierWithoutContents(BracketSpecifierWithoutContents bracketSpecifierWithoutContents) {
    }

    default void visitBracketSpecifierWithQuestionMark(BracketSpecifierWithQuestionMark bracketSpecifierWithQuestionMark) {
    }

    default void visitComparatorExpression(ComparatorExpression comparatorExpression) {
    }

    default void visitSliceExpression(SliceExpression sliceExpression) {
    }

    default void visitComparator(Comparator comparator) {
    }

    default void visitFunctionExpression(FunctionExpression functionExpression) {
    }

    default void visitFunctionArg(FunctionArg functionArg) {
    }

    default void visitCurrentNode(CurrentNodeExpression currentNodeExpression) {
    }

    default void visitExpressionType(ExpressionType expressionType) {
    }

    default void visitRawString(String rawString) {
    }

    default void visitLiteral(Literal literal) {
    }

    default void visitIdentifier(String identifier) {
    }

    default void visitStarExpression(StarExpression star) {
    }

    default void visitJsonValue(JrsValue json) {
    }
}
