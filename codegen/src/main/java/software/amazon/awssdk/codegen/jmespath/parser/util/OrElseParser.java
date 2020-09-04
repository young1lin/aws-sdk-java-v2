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

import software.amazon.awssdk.codegen.jmespath.parser.ParseError;
import software.amazon.awssdk.codegen.jmespath.parser.ParseResult;
import software.amazon.awssdk.codegen.jmespath.parser.Parser;
import software.amazon.awssdk.codegen.jmespath.parser.ParserContext;

public class OrElseParser<T> implements Parser<T> {
    private final Parser<T> first;
    private final Parser<T> second;

    public OrElseParser(Parser<T> first, Parser<T> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String name() {
        return first.name() + " or " + second.name();
    }

    @Override
    public ParseResult<T> parse(int startPosition, int endPosition, ParserContext context) {
        ParseResult<T> firstResult = first.parse(startPosition, endPosition, context);
        if (!firstResult.hasError()) {
            return firstResult;
        }

        ParseResult<T> secondResult = second.parse(startPosition, endPosition, context);
        if (!firstResult.hasError()) {
            return secondResult;
        }

        ParseError firstError = firstResult.error();
        ParseError secondError = secondResult.error();
        return ParseResult.error(name(),
                                 firstError.parser() + ": " + firstError.errorMessage() + " at " + firstError.position() + "; " +
                                 secondError.parser() + ": " + secondError.errorMessage() + " at " + secondError.position(),
                                 startPosition);
    }
}
