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

package software.amazon.awssdk.codegen.poet.waiters;

import com.fasterxml.jackson.jr.stree.JrsBoolean;
import com.fasterxml.jackson.jr.stree.JrsValue;
import com.squareup.javapoet.CodeBlock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import software.amazon.awssdk.codegen.jmespath.component.AndExpression;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifier;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithContents;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithQuestionMark;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifierWithoutContents;
import software.amazon.awssdk.codegen.jmespath.component.ComparatorExpression;
import software.amazon.awssdk.codegen.jmespath.component.CurrentNode;
import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.component.ExpressionType;
import software.amazon.awssdk.codegen.jmespath.component.FunctionArg;
import software.amazon.awssdk.codegen.jmespath.component.FunctionExpression;
import software.amazon.awssdk.codegen.jmespath.component.IndexExpression;
import software.amazon.awssdk.codegen.jmespath.component.Literal;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectHash;
import software.amazon.awssdk.codegen.jmespath.component.MultiSelectList;
import software.amazon.awssdk.codegen.jmespath.component.NotExpression;
import software.amazon.awssdk.codegen.jmespath.component.OrExpression;
import software.amazon.awssdk.codegen.jmespath.component.ParenExpression;
import software.amazon.awssdk.codegen.jmespath.component.PipeExpression;
import software.amazon.awssdk.codegen.jmespath.component.SliceExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpressionRight;
import software.amazon.awssdk.codegen.jmespath.component.WildcardExpression;
import software.amazon.awssdk.codegen.jmespath.parser.JmesPathParser;
import software.amazon.awssdk.codegen.jmespath.parser.JmesPathVisitor;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.waiters.WaitersRuntime;
import software.amazon.awssdk.utils.Validate;

/**
 * A code interpreter for JMESPath expressions.
 *
 * This can convert a JMESPath expression into a statement that executes against an {@link SdkPojo}.
 */
public class JmesPathInterpreter {
    private JmesPathInterpreter() {
    }

    /**
     * Interpret the provided expression into a java statement that executes against the provided input value. This inputValue
     * should be a JMESPath Value in scope.
     */
    public static CodeBlock interpret(String expression, String inputValue) {
        CodeBlock.Builder codeBlock = CodeBlock.builder();
        Visitor visitor = new Visitor(codeBlock, inputValue);
        JmesPathParser.parse(expression).visit(visitor);
        return visitor.codeBlock.build();
    }

    /**
     * An implementation of {@link JmesPathVisitor} used by {@link #interpret(String, String)}.
     */
    private static class Visitor implements JmesPathVisitor {
        private final CodeBlock.Builder codeBlock;
        private final Deque<String> variables = new ArrayDeque<>();
        private int variableIndex = 0;

        private Visitor(CodeBlock.Builder codeBlock, String inputValue) {
            this.codeBlock = codeBlock;
            this.codeBlock.add(inputValue);
            this.variables.push(inputValue);
        }

        @Override
        public void visitExpression(Expression input) {
            input.visit(this);
        }

        @Override
        public void visitSubExpression(SubExpression input) {
            visitExpression(input.leftExpression());
            visitSubExpressionRight(input.rightSubExpression());
        }

        @Override
        public void visitSubExpressionRight(SubExpressionRight input) {
            input.visit(this);
        }

        @Override
        public void visitIndexExpression(IndexExpression input) {
            input.expression().ifPresent(this::visitExpression);
            visitBracketSpecifier(input.bracketSpecifier());
        }

        @Override
        public void visitBracketSpecifier(BracketSpecifier input) {
            input.visit(this);
        }

        @Override
        public void visitBracketSpecifierWithContents(BracketSpecifierWithContents input) {
            if (input.isNumber()) {
                codeBlock.add(".index(" + input.asNumber() + ")");
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public void visitBracketSpecifierWithoutContents(BracketSpecifierWithoutContents input) {
            codeBlock.add(".flatten()");
        }

        @Override
        public void visitBracketSpecifierWithQuestionMark(BracketSpecifierWithQuestionMark input) {
            pushVariable();
            codeBlock.add(".filter($1N -> $1N", currentVariable());
            visitExpression(input.expression());
            codeBlock.add(")");
            popVariable();
        }

        @Override
        public void visitSliceExpression(SliceExpression input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitComparatorExpression(ComparatorExpression input) {
            visitExpression(input.leftExpression());
            codeBlock.add(".compare($S, $N", input.comparator().tokenSymbol(), currentVariable());
            visitExpression(input.rightExpression());
            codeBlock.add(")");
        }

        @Override
        public void visitOrExpression(OrExpression input) {
            visitExpression(input.leftExpression());
            codeBlock.add(".or($N", currentVariable());
            visitExpression(input.rightExpression());
            codeBlock.add(")");
        }

        @Override
        public void visitAndExpression(AndExpression input) {
            visitExpression(input.leftExpression());
            codeBlock.add(".and($N", currentVariable());
            visitExpression(input.rightExpression());
            codeBlock.add(")");
        }

        @Override
        public void visitNotExpression(NotExpression input) {
            codeBlock.add(".constant($N", currentVariable());
            visitExpression(input.expression());
            codeBlock.add(".not())");
        }

        @Override
        public void visitParenExpression(ParenExpression input) {
            visitExpression(input.expression());
        }

        @Override
        public void visitWildcardExpression(WildcardExpression input) {
            codeBlock.add(".wildcard()");
        }

        @Override
        public void visitMultiSelectList(MultiSelectList input) {
            codeBlock.add(".multiSelectList(");

            boolean first = true;
            for (Expression expression : input.expressions()) {
                if (!first) {
                    codeBlock.add(", ");
                } else {
                    first = false;
                }

                pushVariable();
                codeBlock.add("$1N -> $1N", currentVariable());
                visitExpression(expression);
                popVariable();
            }
            codeBlock.add(")");
        }

        @Override
        public void visitMultiSelectHash(MultiSelectHash input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitExpressionType(ExpressionType asExpressionType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitFunctionExpression(FunctionExpression input) {
            switch (input.function()) {
                case "length":
                    visitLengthFunction(input.functionArgs());
                    break;
                case "contains":
                    visitContainsFunction(input.functionArgs());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported function: " + input.function());
            }
        }

        private void visitLengthFunction(List<FunctionArg> functionArgs) {
            Validate.isTrue(functionArgs.size() == 1, "length function only supports 1 parameter.");
            Validate.isTrue(functionArgs.get(0).isExpression(), "length's first parameter must be an expression.");

            visitExpression(functionArgs.get(0).asExpression());
            codeBlock.add(".length()");
        }

        private void visitContainsFunction(List<FunctionArg> functionArgs) {
            Validate.isTrue(functionArgs.size() == 2, "contains function only supports 2 parameter.");
            Validate.isTrue(functionArgs.get(0).isExpression(), "contain's first parameter must be an expression.");
            Validate.isTrue(functionArgs.get(1).isExpression(), "contain's second parameter must be an expression.");

            visitExpression(functionArgs.get(0).asExpression());
            codeBlock.add(".contains($N", currentVariable());
            visitExpression(functionArgs.get(1).asExpression());
            codeBlock.add(")");
        }

        @Override
        public void visitPipeExpression(PipeExpression input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitCurrentNode(CurrentNode input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitRawString(String input) {
            codeBlock.add(".constant($S)", input);
        }

        @Override
        public void visitLiteral(Literal input) {
            JrsValue jsonValue = input.jsonValue();
            if (jsonValue.isNumber()) {
                codeBlock.add(".constant($L)", Integer.parseInt(jsonValue.asText()));
            } else if (jsonValue instanceof JrsBoolean) {
                codeBlock.add(".constant($L)", ((JrsBoolean) jsonValue).booleanValue());
            } else {
                throw new IllegalArgumentException("Unsupported JSON node type: " + input.jsonValue().getClass().getSimpleName());
            }
        }

        @Override
        public void visitIdentifier(String input) {
            codeBlock.add(".field($S)", input);
        }

        @Override
        public void visitNumber(int input) {
            codeBlock.add(".constant($L)", WaitersRuntime.Value.class, input);
        }

        /**
         * Push a variable onto the variable stack. This is used so that lambda expressions can address their closest-scoped
         * variable. For example, the right-hand-side of an AND expression needs to address the same variable as the
         * left-hand-side of the expression.
         */
        public void pushVariable() {
            variables.push("x" + variableIndex++);
        }

        /**
         * Retrieve the current variable on the top of the stack.
         */
        public String currentVariable() {
            return variables.getFirst();
        }

        /**
         * Pop the last variable added from the variable stack.
         */
        public void popVariable() {
            variables.pop();
        }
    }
}
