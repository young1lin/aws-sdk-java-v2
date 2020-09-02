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

import java.util.List;
import software.amazon.awssdk.utils.Validate;

public class BracketSpecifierWithContents {
    private Integer number;
    private StarExpression starExpression;
    private SliceExpression sliceExpression;

    private BracketSpecifierWithContents() {
    }

    public static BracketSpecifierWithContents number(Integer number) {
        Validate.notNull(number, "number");
        BracketSpecifierWithContents result = new BracketSpecifierWithContents();
        result.number = number;
        return result;
    }

    public static BracketSpecifierWithContents starExpression(StarExpression starExpression) {
        Validate.notNull(starExpression, "starExpression");
        BracketSpecifierWithContents result = new BracketSpecifierWithContents();
        result.starExpression = starExpression;
        return result;
    }

    public static BracketSpecifierWithContents sliceExpression(SliceExpression sliceExpression) {
        Validate.notNull(sliceExpression, "sliceExpression");
        BracketSpecifierWithContents result = new BracketSpecifierWithContents();
        result.sliceExpression = sliceExpression;
        return result;
    }


    public boolean isInteger() {
        return number != null;
    }

    public boolean isStarExpression() {
        return starExpression != null;
    }

    public boolean isSliceExpression() {
        return sliceExpression != null;
    }

    public int asInteger() {
        Validate.validState(isInteger(), "Not a Integer");
        return number;
    }

    public StarExpression asStarExpression() {
        Validate.validState(isStarExpression(), "Not a StarExpression");
        return starExpression;
    }

    public SliceExpression asSliceExpression() {
        Validate.validState(isSliceExpression(), "Not a SliceExpression");
        return sliceExpression;
    }

}
