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

import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.codegen.jmespath.parser.ParseError;
import software.amazon.awssdk.codegen.jmespath.parser.ParseResult;
import software.amazon.awssdk.codegen.jmespath.parser.Parser;

public final class CompositeParser<T> implements Parser<T> {
    private final String expectedType;
    private final List<Parser<T>> parsers;

    @SafeVarargs
    public CompositeParser(String expectedType, Parser<T>... parsers) {
        this.expectedType = expectedType;
        this.parsers = Arrays.asList(parsers);
    }

    @Override
    public ParseResult<T> parse(int startPosition, int endPosition) {
        //                ++errorIndentationLevel;

        StringBuilder indentation = new StringBuilder();
        //                for (int i = 0; i < errorIndentationLevel * 2; i++) {
        //                    indentation.append(' ');
        //                }

        StringBuilder errorMessage = new StringBuilder();
        for (Parser<T> parseCall : parsers) {
            ParseResult<T> parseResult = parseCall.parse(startPosition, endPosition);

            if (parseResult.hasResult()) {
                return parseResult;
            } else {
                ParseError error = parseResult.getError();

                String parseErrorMessage = removeFormatting(error.errorMessage());
                errorMessage.append(indentation).append("Not a ").append(error.parser()).append(" at ")
                            .append(error.position()).append(":\n")
                            .append(indentation).append("  ").append(parseErrorMessage).append("\n");
            }
        }

        //                --errorIndentationLevel;

        return ParseResult.error(expectedType, errorMessage.toString(), startPosition);
    }

    private String removeFormatting(String error) {
        int firstNonSpace = 0;
        while (firstNonSpace < error.length() && error.charAt(firstNonSpace) == ' ') {
            ++firstNonSpace;
        }
        int lastCharExclusive = error.length();
        if (error.endsWith("\n")) {
            --lastCharExclusive;
        }

        return error.substring(firstNonSpace, lastCharExclusive);
    }
}