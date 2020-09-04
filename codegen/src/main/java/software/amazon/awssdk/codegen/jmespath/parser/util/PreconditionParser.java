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

import java.util.Optional;
import software.amazon.awssdk.codegen.jmespath.parser.ParseError;
import software.amazon.awssdk.codegen.jmespath.parser.ParseResult;
import software.amazon.awssdk.codegen.jmespath.parser.Parser;
import software.amazon.awssdk.codegen.jmespath.parser.ParserContext;

public abstract class PreconditionParser<T> extends DelegatingParser<T> {
    public PreconditionParser(Parser<T> delegate) {
        super(delegate);
    }

    @Override
    public final ParseResult<T> parse(int startPosition, int endPosition, ParserContext context) {
        Optional<ParseError> parseError = enforcePrecondition(startPosition, endPosition, context);

        if (!parseError.isPresent()) {
            return delegate.parse(startPosition, endPosition, context);
        } else {
            return ParseResult.error(parseError.get());
        }
    }

    protected abstract Optional<ParseError> enforcePrecondition(int startPosition, int endPosition, ParserContext context);
}
