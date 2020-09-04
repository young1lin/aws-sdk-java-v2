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
import software.amazon.awssdk.utils.Pair;

public class FixedLengthParsers<T> {
    private final FixedLengthParser<T> parser;

    private FixedLengthParsers(FixedLengthParser<T> parser) {
        this.parser = parser;
    }

    public static <T> FixedLengthParsers<T> first(Parser<T> parser, int startPosition, int endPosition) {
        return new FixedLengthParsers<>(new DefaultFixedLengthParser<>(parser, startPosition, endPosition));
    }

    public <U> FixedLengthParsers<Pair<T, U>> then(Parser<U> nextParser, int startPosition, int endPosition) {
        FixedLengthParser<U> next = new DefaultFixedLengthParser<>(nextParser, startPosition, endPosition);
        return new FixedLengthParsers<>(new SequencedFixedLengthParser<>(parser, next));
    }

    public ParseResult<T> parse(ParserContext context) {
        return parser.parse(context);
    }

    private interface FixedLengthParser<T> {
        ParseResult<T> parse(ParserContext context);
    }

    private static class DefaultFixedLengthParser<T> implements FixedLengthParser<T> {
        private final Parser<T> delegate;
        private final int startPosition;
        private final int endPosition;

        private DefaultFixedLengthParser(Parser<T> delegate, int startPosition, int endPosition) {
            this.delegate = delegate;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
        }

        public ParseResult<T> parse(ParserContext context) {
            return delegate.parse(startPosition, endPosition, context);
        }
    }

    private static class SequencedFixedLengthParser<T, U> implements FixedLengthParser<Pair<T, U>> {
        private final FixedLengthParser<T> first;
        private final FixedLengthParser<U> second;

        private SequencedFixedLengthParser(FixedLengthParser<T> first, FixedLengthParser<U> second) {
            this.first = first;
            this.second = second;
        }

        public ParseResult<Pair<T, U>> parse(ParserContext context) {
            ParseResult<T> firstParse = first.parse(context);
            if (firstParse.hasError()) {
                return ParseResult.error(firstParse.error());
            }

            ParseResult<U> secondParse = second.parse(context);
            if (secondParse.hasError()) {
                return ParseResult.error(secondParse.error());
            }

            return ParseResult.success(Pair.of(firstParse.result(), secondParse.result()));
        }
    }
}
