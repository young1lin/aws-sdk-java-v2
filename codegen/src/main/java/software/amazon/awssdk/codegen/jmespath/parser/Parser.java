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

import java.util.function.Function;
import software.amazon.awssdk.codegen.jmespath.parser.util.AndThenParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.ConvertingParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.OrElseParser;

public interface Parser<T> {
    String name();

    ParseResult<T> parse(int startPosition, int endPosition, ParserContext context);

    default <U> Parser<U> map(Function<T, U> mapper) {
        return new ConvertingParser<>(this, mapper);
    }

    default Parser<T> orElse(Parser<T> fallbackParser) {
        return new OrElseParser<>(this, fallbackParser);
    }

    default <U> Parser<U> andThen(Function<T, ParseResult<U>> onSuccessFunction) {
        return new AndThenParser<>(this, onSuccessFunction);
    }
}
