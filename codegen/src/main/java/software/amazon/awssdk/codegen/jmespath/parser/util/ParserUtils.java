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

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.codegen.jmespath.parser.ParserContext;

public class ParserUtils {
    public static int charsInRange(int startPosition, int endPosition) {
        return endPosition - startPosition;
    }

    public static int trimLeftWhitespace(int startPosition, int endPosition, ParserContext context) {
        while (context.input().charAt(startPosition) == ' ' && startPosition < endPosition - 1) {
            ++startPosition;
        }

        return startPosition;
    }

    public static int trimRightWhitespace(int startPosition, int endPosition, ParserContext context) {
        while (context.input().charAt(endPosition - 1) == ' ' && startPosition < endPosition - 1) {
            --endPosition;
        }

        return endPosition;
    }

    public static List<Integer> findCharacters(int startPosition, int endPosition, ParserContext context, String symbol) {
        List<Integer> results = new ArrayList<>();

        int start = startPosition;
        while (true) {
            int match = context.input().indexOf(symbol, start);
            if (match < 0 || match >= endPosition) {
                break;
            }
            results.add(match);
            start = match + 1;
        }

        return results;
    }
}
