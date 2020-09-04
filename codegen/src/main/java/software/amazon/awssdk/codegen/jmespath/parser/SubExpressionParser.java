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

import java.util.List;
import software.amazon.awssdk.codegen.jmespath.component.SubExpression;
import software.amazon.awssdk.codegen.jmespath.component.SubExpressionRight;
import software.amazon.awssdk.codegen.jmespath.parser.util.CheckBoundsParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.FixedLengthParsers;
import software.amazon.awssdk.codegen.jmespath.parser.util.Parsers;
import software.amazon.awssdk.codegen.jmespath.parser.util.ParserUtils;
import software.amazon.awssdk.codegen.jmespath.parser.util.TrimWhitespaceParser;

public class SubExpressionParser implements Parser<SubExpression> {
    private static final Parser<SubExpression> PARSER =
        new TrimWhitespaceParser<>(new CheckBoundsParser<>(new SubExpressionParser()));

    private SubExpressionParser() {
    }

    public static Parser<SubExpression> instance() {
        return PARSER;
    }

    @Override
    public String name() {
        return "sub-expression";
    }

    @Override
    public ParseResult<SubExpression> parse(int startPosition, int endPosition, ParserContext context) {
        List<Integer> dotPositions = ParserUtils.findCharacters(startPosition + 1, endPosition - 1, context, ".");
        for (Integer dotPosition : dotPositions) {
            ParseResult<SubExpression> parserOutput =
                FixedLengthParsers.first(ExpressionParser.instance(), startPosition, dotPosition)
                                  .then(SubExpressionRightParser.instance(), dotPosition + 1, endPosition)
                                  .parse(context)
                                  .mapResult(x -> new SubExpression(x.left(), x.right()));

            if (parserOutput.hasResult()) {
                return ParseResult.success(parserOutput.result());
            }
        }

        return ParseResult.error(name(), "Invalid sub-expression", startPosition);
    }
}
