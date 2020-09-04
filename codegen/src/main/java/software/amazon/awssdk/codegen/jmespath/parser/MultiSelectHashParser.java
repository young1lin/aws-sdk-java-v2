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

import software.amazon.awssdk.codegen.jmespath.component.MultiSelectHash;
import software.amazon.awssdk.codegen.jmespath.parser.util.CheckBoundsParser;
import software.amazon.awssdk.codegen.jmespath.parser.util.TrimWhitespaceParser;

public class MultiSelectHashParser implements Parser<MultiSelectHash> {
    private static final Parser<MultiSelectHash> PARSER =
        new TrimWhitespaceParser<>(new CheckBoundsParser<>(new MultiSelectHashParser()));

    private MultiSelectHashParser() {
    }

    public static Parser<MultiSelectHash> instance() {
        return PARSER;
    }

    @Override
    public String name() {
        return "expression";
    }

    @Override
    public ParseResult<MultiSelectHash> parse(int startPosition, int endPosition, ParserContext context) {
    }
}
