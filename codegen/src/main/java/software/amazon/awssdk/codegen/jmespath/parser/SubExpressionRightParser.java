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

import software.amazon.awssdk.codegen.jmespath.component.SubExpressionRight;
import software.amazon.awssdk.codegen.jmespath.parser.util.CheckBoundsParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.Parsers;
import software.amazon.awssdk.codegen.jmespath.parser.util.TrimWhitespaceParser;

public class SubExpressionRightParser implements Parser<SubExpressionRight> {
    private static final Parser<SubExpressionRight> PARSER =
        new TrimWhitespaceParser<>(new CheckBoundsParser<>(new SubExpressionRightParser()));

    private SubExpressionRightParser() {
    }

    public static Parser<SubExpressionRight> instance() {
        return PARSER;
    }

    @Override
    public String name() {
        return "sub-expression-right";
    }

    @Override
    public ParseResult<SubExpressionRight> parse(int startPosition, int endPosition, ParserContext context) {
        return Parsers.firstTry(IdentifierParser.instance().map(SubExpressionRight::identifier))
                      .thenTry(MultiSelectListParser.instance().map(SubExpressionRight::multiSelectList))
                      .thenTry(MultiSelectHashParser.instance().map(SubExpressionRight::multiSelectHash))
                      .thenTry(FunctionExpressionParser.instance().map(SubExpressionRight::functionExpression))
                      .thenTry(StarExpressionParser.instance().map(SubExpressionRight::starExpression))
                      .parse(startPosition, endPosition, context);
    }
}
