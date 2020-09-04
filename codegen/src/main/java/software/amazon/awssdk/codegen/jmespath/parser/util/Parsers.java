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

import software.amazon.awssdk.codegen.jmespath.parser.Parser;
import software.amazon.awssdk.utils.Pair;

public class Parsers<T> extends DelegatingParser<T> {
    private Parsers(Parser<T> parser) {
        super(parser);
    }

    public static <T> Parsers<T> firstTry(Parser<T> parser) {
        return new Parsers<>(parser);
    }

    public Parsers<T> thenTry(Parser<T> nextParser) {
        return new Parsers<>(new OrElseParser<>(delegate, nextParser));
    }

    public <U> Parsers<Pair<T, U>> ifSuccessful(Parser<U> nextParser) {
        return new Parsers<>(new AndThenParser<>(delegate, nextParser));
    }
}
