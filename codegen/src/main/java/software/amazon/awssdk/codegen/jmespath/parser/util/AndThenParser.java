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

package software.amazon.awssdk.codegen.jmespath.parser.util;

import software.amazon.awssdk.codegen.jmespath.parser.ParseResult;
import software.amazon.awssdk.codegen.jmespath.parser.Parser;
import software.amazon.awssdk.codegen.jmespath.parser.ParserContext;

public class AndThenParser<T, U> implements Parser<Pair<T, U>> {
    private final Parser<T> firstParser;
    private final Parser<U> secondParser;

    public AndThenParser(Parser<T> firstParser, Parser<U> secondParser) {
        this.firstParser = firstParser;
        this.secondParser = secondParser;
    }

    @Override
    public String name() {
        return firstParser.name() + " and then " + secondParser.name();
    }

    @Override
    public ParseResult<Pair<T, U>> parse(int startPosition, int endPosition, ParserContext context) {
        ParseResult<T> firstResult = firstParser.parse(startPosition, endPosition, context);
        if (firstResult.hasError()) {
            return ParseResult.error(firstResult.error());
        }

        ParseResult<U> secondResult = secondParser.parse(startPosition, endPosition, context);
        if (secondResult.hasError()) {
            return ParseResult.error(secondResult.error());
        }

        return ParseResult.success(new Pair<>(firstResult.result(), secondResult.result()));

    }
}
