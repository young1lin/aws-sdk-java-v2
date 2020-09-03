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

package software.amazon.awssdk.codegen.jmespath.component;

import software.amazon.awssdk.utils.Validate;

public class BracketSpecifier {
    private BracketSpecifierWithContents bracketSpecifierWithContents;
    private BracketSpecifierWithoutContents bracketSpecifierWithoutContents;
    private BracketSpecifierWithQuestionMark bracketSpecifierWithQuestionMark;

    public static BracketSpecifier withContents(BracketSpecifierWithContents bracketSpecifierWithContents) {
        Validate.notNull(bracketSpecifierWithContents, "bracketSpecifierWithContents");
        BracketSpecifier result = new BracketSpecifier();
        result.bracketSpecifierWithContents = bracketSpecifierWithContents;
        return result;
    }

    public static BracketSpecifier withoutContents() {
        BracketSpecifier result = new BracketSpecifier();
        result.bracketSpecifierWithoutContents = new BracketSpecifierWithoutContents();
        return result;
    }

    public static BracketSpecifier withQuestionMark(BracketSpecifierWithQuestionMark bracketSpecifierWithQuestionMark) {
        Validate.notNull(bracketSpecifierWithQuestionMark, "bracketSpecifierWithQuestionMark");
        BracketSpecifier result = new BracketSpecifier();
        result.bracketSpecifierWithQuestionMark = bracketSpecifierWithQuestionMark;
        return result;
    }

    public boolean isBracketSpecifierWithContents() {
        return bracketSpecifierWithContents != null;
    }

    public boolean isBracketSpecifierWithoutContents() {
        return bracketSpecifierWithoutContents != null;
    }

    public boolean isBracketSpecifierWithQuestionMark() {
        return bracketSpecifierWithQuestionMark != null;
    }

    public BracketSpecifierWithContents asBracketSpecifierWithContents() {
        Validate.validState(isBracketSpecifierWithContents(), "Not a BracketSpecifierWithContents");
        return bracketSpecifierWithContents;
    }

    public BracketSpecifierWithoutContents asBracketSpecifierWithoutContents() {
        Validate.validState(isBracketSpecifierWithoutContents(), "Not a BracketSpecifierWithoutContents");
        return bracketSpecifierWithoutContents;
    }

    public BracketSpecifierWithQuestionMark asBracketSpecifierWithQuestionMark() {
        Validate.validState(isBracketSpecifierWithQuestionMark(), "Not a BracketSpecifierWithQuestionMark");
        return bracketSpecifierWithQuestionMark;
    }

//    void visit(Visitor visitor);
//
//    interface Visitor {
//        default void visitBracketSpecifierWithContents(BracketSpecifierWithContents bracketSpecifierWithContents) {
//        }
//
//        default void visitBracketSpecifierWithoutContents(BracketSpecifierWithoutContents bracketSpecifierWithContents) {
//        }
//
//        default void visitBracketSpecifierWithQuestionMark(BracketSpecifierWithQuestionMark bracketSpecifierWithContents) {
//        }
//    }
}
