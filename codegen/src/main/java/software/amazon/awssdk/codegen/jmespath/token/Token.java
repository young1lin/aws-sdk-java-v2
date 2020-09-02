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

package software.amazon.awssdk.codegen.jmespath.token;

import software.amazon.awssdk.utils.Validate;

public class Token {
    private final int position;
    private final String string;
    private final Character symbol;

    public Token(String string, Character symbol, int position) {
        this.string = string;
        this.symbol = symbol;
        this.position = position;
    }

    public static Token string(String string, int position) {
        Validate.notNull(string, "string");
        return new Token(string, null, position);
    }

    public static Token symbol(Character symbol, int position) {
        Validate.notNull(symbol, "symbol");
        return new Token(null, symbol, position);
    }

    public boolean isString() {
        return string != null;
    }

    public boolean isSymbol() {
        return symbol != null;
    }

    public String asString() {
        Validate.notNull(string, "string");
        return string;
    }

    public char asSymbol() {
        Validate.notNull(symbol, "symbol");
        return symbol;
    }

    public int position() {
        return position;
    }

    public void visit(Visitor visitor) {
        if (isString()) {
            visitor.visitString(asString());
        } else if (isSymbol()) {
            visitor.visitSymbol(asSymbol());
        } else {
            throw new IllegalStateException();
        }
    }

    public interface Visitor {
        default void visitString(String string) {
        }

        default void visitSymbol(char symbol) {
        }
    }
}
