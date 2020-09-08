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

package software.amazon.awssdk.codegen.poet.waiters;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class JmesPathInterpreterTest {
    @Test
    public void test() {
        testConversion("foo.bar", "input.field(\"foo\").field(\"bar\")");

        testConversion("foo[].bar", "input.field(\"foo\").flatten().field(\"bar\")");

        testConversion("foo[0]", "input.field(\"foo\").index(0)");

        testConversion("length(foo[].bar) > `0`",
                       "input.field(\"foo\").flatten().field(\"bar\").length().compare(\">\", input.constant(0))");

        testConversion("contains(AutoScalingGroups[].[length(Instances[?LifecycleState=='InService']) >= MinSize][], `false`)",
                       "input.field(\"AutoScalingGroups\").flatten().multiSelectList(x0 -> x0.field(\"Instances\").filter(x1 -> "
                       + "x1.field(\"LifecycleState\").compare(\"==\", x1.constant(\"InService\"))).length().compare(\">=\", "
                       + "x0.field(\"MinSize\"))).flatten().contains(input.constant(false))");

        testConversion("length(services[?!(length(deployments) == `1` && runningCount == desiredCount)]) == `0`",
                       "input.field(\"services\").filter(x0 -> x0.constant(x0.field(\"deployments\").length().compare(\"==\", "
                       + "x0.constant(1)).and(x0.field(\"runningCount\").compare(\"==\", x0.field(\"desiredCount\"))).not()))"
                       + ".length().compare(\"==\", input.constant(0))");

        testConversion("VerificationAttributes.*.VerificationStatus",
                       "input.field(\"VerificationAttributes\").wildcard().field(\"VerificationStatus\")");
    }

    private static void testConversion(String jmesPathString, String expectedCode) {
        assertThat(JmesPathInterpreter.interpret(jmesPathString, "input").toString()).isEqualTo((expectedCode));
    }

}