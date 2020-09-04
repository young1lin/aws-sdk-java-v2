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

public final class ParseError {
    private final String parser;
    private final String errorMessage;
    private final int position;

    public ParseError(String parser, String errorMessage, int position) {
        this.parser = parser;
        this.errorMessage = errorMessage;
        this.position = position;
    }

    public String parser() {
        return parser;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public int position() {
        return position;
    }
}
