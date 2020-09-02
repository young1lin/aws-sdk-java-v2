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

package software.amazon.awssdk.codegen.jmespath;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.codegen.jmespath.token.Token;

public class JmesPathLexer {
    public static List<Token> tokenize(String input) {
        List<Token> result = new ArrayList<>();

        StringBuilder stringContents = new StringBuilder();

        for (int i = 0; i < input.length(); ++i) {
            char character = input.charAt(i);
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException("Unsupported character at index " + i + ".");
            }

            if (Character.isLetterOrDigit(character)) {
                stringContents.append(character);
            } else {
                if (stringContents.length() > 0) {
                    String string = stringContents.toString();
                    result.add(Token.string(string, i - string.length()));
                    stringContents.setLength(0);
                }

                result.add(Token.symbol(character, i));
            }
        }

        if (stringContents.length() > 0) {
            String string = stringContents.toString();
            result.add(Token.string(string, input.length() - string.length()));
        }

        return result;
    }
}
