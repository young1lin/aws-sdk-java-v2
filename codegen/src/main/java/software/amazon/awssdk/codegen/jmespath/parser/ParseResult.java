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
import java.util.function.Supplier;
import software.amazon.awssdk.utils.Validate;

public final class ParseResult<T> {
    private final T result;
    private final ParseError error;

    private ParseResult(T result, ParseError error) {
        this.result = result;
        this.error = error;
    }

    public static <T> ParseResult<T> success(T result) {
        return new ParseResult<>(result, null);
    }

    public static <T> ParseResult<T> error(String parser, String errorMessage, int position) {
        Validate.notNull(errorMessage, "errorMessage");
        return error(new ParseError(parser, errorMessage, position));
    }

    public static <T> ParseResult<T> error(ParseError error) {
        Validate.notNull(error, "error");
        return new ParseResult<>(null, error);
    }

    public <U> ParseResult<U> ifSuccessful(Supplier<ParseResult<U>> ifSuccess) {
        if (hasError()) {
            return ParseResult.error(error);
        } else {
            return ifSuccess.get();
        }
    }

    public <U> ParseResult<U> mapResult(Function<T, U> mapper) {
        if (hasError()) {
            return ParseResult.error(error);
        } else {
            return ParseResult.success(mapper.apply(result));
        }
    }

    public <U> ParseResult<U> flatMapResult(Function<T, ParseResult<U>> mapper) {
        if (hasError()) {
            return ParseResult.error(error);
        } else {
            return mapper.apply(result);
        }
    }

    public ParseResult<T> orElse(Supplier<ParseResult<T>> orElse) {
        if (hasError()) {
            return orElse.get();
        } else {
            return this;
        }
    }

    public boolean hasResult() {
        return result != null;
    }

    public boolean hasError() {
        return error != null;
    }

    public T result() {
        Validate.validState(hasResult(), "Result not available");
        return result;
    }

    public ParseError error() {
        Validate.validState(hasError(), "Error not available");
        return error;
    }
}
