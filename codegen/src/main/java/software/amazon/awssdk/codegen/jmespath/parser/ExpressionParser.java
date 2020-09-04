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

package software.amazon.awssdk.codegen.jmespath.parser;

import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.parser.util.CheckBoundsParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.Parsers;
import software.amazon.awssdk.codegen.jmespath.parser.util.TrimWhitespaceParser;

public class ExpressionParser implements Parser<Expression> {
    private static final Parser<Expression> PARSER =
        new TrimWhitespaceParser<>(new CheckBoundsParser<>(new ExpressionParser()));

    private ExpressionParser() {
    }

    public static Parser<Expression> instance() {
        return PARSER;
    }

    @Override
    public String name() {
        return "expression";
    }

    @Override
    public ParseResult<Expression> parse(int startPosition, int endPosition, ParserContext context) {
        return Parsers.firstTry(SubExpressionParser.instance().map(Expression::subExpression))
                      .thenTry(IndexExpressionParser.instance().map(Expression::indexExpression))
                      .thenTry(ComparatorExpressionParser.instance().map(Expression::comparatorExpression))
                      .thenTry(OrExpressionParser.instance().map(Expression::orExpression))
                      .thenTry(IdentifierParser.instance().map(Expression::identifier))
                      .thenTry(AndExpressionParser.instance().map(Expression::andExpression))
                      .thenTry(NotExpressionParser.instance().map(Expression::notExpression))
                      .thenTry(ParenExpressionParser.instance().map(Expression::parenExpression))
                      .thenTry(StarExpressionParser.instance().map(Expression::starExpression))
                      .thenTry(MultiSelectListParser.instance().map(Expression::multiSelectList))
                      .thenTry(MultiSelectHashParser.instance().map(Expression::multiSelectHash))
                      .thenTry(LiteralParser.instance().map(Expression::literal))
                      .thenTry(FunctionExpressionParser.instance().map(Expression::functionExpression))
                      .thenTry(PipeExpressionParser.instance().map(Expression::pipeExpression))
                      .thenTry(RawStringParser.instance().map(Expression::rawString))
                      .thenTry(CurrentNodeExpressionParser.instance().map(Expression::currentNode))
                      .parse(startPosition, endPosition, context);
    }
}
