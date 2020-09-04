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
import java.util.function.Function;
import software.amazon.awssdk.codegen.jmespath.component.BracketSpecifier;
import software.amazon.awssdk.codegen.jmespath.component.Expression;
import software.amazon.awssdk.codegen.jmespath.component.IndexExpression;
import software.amazon.awssdk.codegen.jmespath.parser.util.CheckBoundsParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.ConvertingParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.TrimWhitespaceParser;

public class IndexExpressionWithLhsParser implements Parser<IndexExpression> {
    private static final Parser<IndexExpression> PARSER =
        new TrimWhitespaceParser<>(new CheckBoundsParser<>(new IndexExpressionWithLhsParser()));

    private IndexExpressionWithLhsParser() {
    }

    public static Parser<IndexExpression> instance() {
        return PARSER;
    }

    @Override
    public String name() {
        return "expression";
    }

    @Override
    public ParseResult<IndexExpression> parse(int startPosition, int endPosition, ParserContext context) {
        List<Integer> bracketPositions = ParserUtils.findCharacters(startPosition + 1, endPosition - 1, "[");
        for (Integer bracketPosition : bracketPositions) {
            ParseResult<Expression> leftSide = parseExpression(startPosition, bracketPosition);
            if (leftSide.hasError()) {
                continue;
            }

            ParseResult<BracketSpecifier> rightSide = parseBracketSpecifier(bracketPosition, endPosition);
            if (rightSide.hasError()) {
                continue;
            }

            return ParseResult.success(IndexExpression.indexExpression(leftSide.result(), rightSide.result()));
        }

        return ParseResult.error("index-expression with lhs-expression", "Invalid index-expression with lhs-expression",
                                 startPosition);
    }
}
