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

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static software.amazon.awssdk.utils.internal.CodegenNamingUtils.lowercaseFirstChar;

import com.fasterxml.jackson.jr.stree.JrsString;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import software.amazon.awssdk.annotations.SdkInternalApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.codegen.model.intermediate.IntermediateModel;
import software.amazon.awssdk.codegen.model.intermediate.OperationModel;
import software.amazon.awssdk.codegen.model.service.Acceptor;
import software.amazon.awssdk.codegen.model.service.WaiterDefinition;
import software.amazon.awssdk.codegen.poet.ClassSpec;
import software.amazon.awssdk.codegen.poet.PoetUtils;
import software.amazon.awssdk.core.internal.waiters.WaiterAttribute;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.waiters.PollingStrategy;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.core.waiters.WaiterState;
import software.amazon.awssdk.core.waiters.WaitersRuntime;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.SdkAutoCloseable;

/**
 * Base class containing common logic shared between the sync waiter class and the async waiter class
 */
public abstract class BaseWaiterClassSpec implements ClassSpec {

    private final IntermediateModel model;
    private final String modelPackage;
    private final Map<String, WaiterDefinition> waiters;
    private final ClassName waiterClassName;

    public BaseWaiterClassSpec(IntermediateModel model, ClassName waiterClassName) {
        this.model = model;
        this.modelPackage = model.getMetadata().getFullModelPackageName();
        this.waiters = model.getWaiters();
        this.waiterClassName = waiterClassName;
    }

    @Override
    public TypeSpec poetSpec() {
        TypeSpec.Builder typeSpecBuilder = PoetUtils.createClassBuilder(className());
        typeSpecBuilder.addAnnotation(SdkInternalApi.class);
        typeSpecBuilder.addAnnotation(ThreadSafe.class);
        typeSpecBuilder.addModifiers(FINAL);
        typeSpecBuilder.addSuperinterface(interfaceClassName());
        typeSpecBuilder.addMethod(constructor());
        typeSpecBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(WaiterAttribute.class, SdkAutoCloseable.class),
                                                   "CLIENT_ATTRIBUTE", PRIVATE, STATIC, FINAL)
                                          .initializer("new $T<>($T.class)", WaiterAttribute.class, SdkAutoCloseable.class)
                                          .build());
        typeSpecBuilder.addField(clientClassName(), "client", PRIVATE, FINAL);
        typeSpecBuilder.addField(ClassName.get(AttributeMap.class), "managedResources", PRIVATE, FINAL);
        typeSpecBuilder.addMethod(staticErrorCodeMethod());
        typeSpecBuilder.addMethods(waiterOperations());
        typeSpecBuilder.addMethods(waiterAcceptorInitializers());
        typeSpecBuilder.addFields(waitersFields());
        additionalTypeSpecModification(typeSpecBuilder);

        typeSpecBuilder.addMethod(closeMethod());

        typeSpecBuilder.addMethod(MethodSpec.methodBuilder("builder")
                                            .addModifiers(Modifier.PUBLIC, STATIC)
                                            .returns(interfaceClassName().nestedClass("Builder"))
                                            .addStatement("return new DefaultBuilder()")
                                            .build());

        typeSpecBuilder.addType(builder());
        return typeSpecBuilder.build();
    }

    private MethodSpec closeMethod() {
        return MethodSpec.methodBuilder("close")
                         .addAnnotation(Override.class)
                         .addModifiers(PUBLIC)
                         .addStatement("managedResources.close()")
                         .build();
    }

    protected abstract ClassName clientClassName();

    protected abstract TypeName getWaiterResponseType(OperationModel opModel);

    protected abstract ClassName interfaceClassName();

    protected void additionalTypeSpecModification(TypeSpec.Builder type) {
        // no-op
    }

    protected void additionalConstructorInitialization(MethodSpec.Builder method) {
        // no-op
    }

    protected void additionalBuilderTypeSpecModification(TypeSpec.Builder builder) {
        // no-op
    }

    protected Optional<String> additionalWaiterConfig() {
        return Optional.empty();
    }

    private MethodSpec constructor() {
        MethodSpec.Builder ctor = MethodSpec.constructorBuilder()
                                            .addModifiers(PRIVATE)
                                            .addParameter(className().nestedClass("DefaultBuilder"), "builder");
        ctor.addStatement("$T attributeMapBuilder = $T.builder()", ClassName.get(AttributeMap.class).nestedClass("Builder"),
                          AttributeMap.class);
        ctor.beginControlFlow("if (builder.client == null)")
            .addStatement("this.client = $T.builder().build()", clientClassName())
            .addStatement("attributeMapBuilder.put(CLIENT_ATTRIBUTE, this.client)")
            .endControlFlow();
        ctor.beginControlFlow("else")
            .addStatement("this.client = builder.client")
            .endControlFlow();

        additionalConstructorInitialization(ctor);

        ctor.addStatement("managedResources = attributeMapBuilder.build()");

        waiters.entrySet().stream()
               .map(this::waiterFieldInitialization)
               .forEach(ctor::addCode);

        return ctor.build();
    }

    private CodeBlock waiterFieldInitialization(Map.Entry<String, WaiterDefinition> waiterDefinition) {
        String waiterKey = waiterDefinition.getKey();
        String waiterName = lowercaseFirstChar(waiterKey);
        WaiterDefinition waiter = waiterDefinition.getValue();
        String pollingStrategyVarName = waiterName + "Strategy";
        OperationModel opModel = operationModel(waiter);
        CodeBlock.Builder codeBlockBuilder = CodeBlock
            .builder()
            .addStatement("$T $N = builder.pollingStrategy == null ? $T.builder().maxAttempts($L)"
                          + ".backoffStrategy($T.create($T.ofSeconds($L))).build() : builder.pollingStrategy",
                          PollingStrategy.class,
                          pollingStrategyVarName,
                          PollingStrategy.class,
                          waiter.getMaxAttempts(),
                          FixedDelayBackoffStrategy.class,
                          Duration.class,
                          waiter.getDelay());


        String waiterFieldName = waiterFieldName(waiterKey);
        codeBlockBuilder.add("this.$L = $T.builder($T.class).pollingStrategy($L).acceptors($LAcceptors())",
                             waiterFieldName,
                             waiterClassName,
                             ClassName.get(modelPackage, opModel.getReturnType().getReturnType()),
                             pollingStrategyVarName,
                             waiterFieldName);

        additionalWaiterConfig().ifPresent(codeBlockBuilder::add);
        codeBlockBuilder.addStatement(".build()");
        return codeBlockBuilder.build();
    }

    private List<FieldSpec> waitersFields() {
        return waiters.entrySet().stream()
                      .map(this::waiterField)
                      .collect(Collectors.toList());
    }

    private FieldSpec waiterField(Map.Entry<String, WaiterDefinition> waiterDefinition) {
        OperationModel opModel = operationModel(waiterDefinition.getValue());
        ClassName pojoResponse = ClassName.get(modelPackage, opModel.getReturnType().getReturnType());
        String fieldName = waiterFieldName(waiterDefinition.getKey());
        return FieldSpec.builder(ParameterizedTypeName.get(waiterClassName,
                                                           pojoResponse), fieldName)
                        .addModifiers(PRIVATE, FINAL)
                        .build();
    }

    private TypeSpec builder() {
        TypeSpec.Builder builder = TypeSpec.classBuilder("DefaultBuilder")
                                           .addModifiers(PUBLIC, STATIC, FINAL)
                                           .addSuperinterface(interfaceClassName().nestedClass("Builder"))
                                           .addField(clientClassName(), "client", PRIVATE)
                                           .addField(ClassName.get(PollingStrategy.class), "pollingStrategy", PRIVATE);

        additionalBuilderTypeSpecModification(builder);
        builder.addMethods(builderMethods());
        builder.addMethod(MethodSpec.constructorBuilder()
                                    .addModifiers(PRIVATE)
                                    .build());
        return builder.build();
    }

    private List<MethodSpec> builderMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MethodSpec.methodBuilder("pollingStrategy")
                              .addModifiers(Modifier.PUBLIC)
                              .addAnnotation(Override.class)
                              .addParameter(ClassName.get(PollingStrategy.class), "pollingStrategy")
                              .addStatement("this.pollingStrategy = pollingStrategy")
                              .addStatement("return this")
                              .returns(interfaceClassName().nestedClass("Builder"))
                              .build());
        methods.add(MethodSpec.methodBuilder("client")
                              .addModifiers(Modifier.PUBLIC)
                              .addAnnotation(Override.class)
                              .addParameter(clientClassName(), "client")
                              .addStatement("this.client = client")
                              .addStatement("return this")
                              .returns(interfaceClassName().nestedClass("Builder"))
                              .build());
        methods.add(MethodSpec.methodBuilder("build")
                              .addModifiers(Modifier.PUBLIC)
                              .returns(interfaceClassName())
                              .addStatement("return new $T(this)", className())
                              .build());
        return methods;

    }

    private List<MethodSpec> waiterOperations() {
        return waiters.entrySet()
                      .stream()
                      .flatMap(this::waiterOperations)
                      .sorted(Comparator.comparing(m -> m.name))
                      .collect(Collectors.toList());
    }

    private Stream<MethodSpec> waiterOperations(Map.Entry<String, WaiterDefinition> waiterDefinition) {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(waiterOperation(waiterDefinition));
        return methods.stream();
    }

    private MethodSpec waiterOperation(Map.Entry<String, WaiterDefinition> waiterDefinition) {
        String waiterMethodName = waiterDefinition.getKey();
        OperationModel opModel = operationModel(waiterDefinition.getValue());

        ClassName requestType = ClassName.get(modelPackage, opModel.getInput().getVariableType());

        MethodSpec.Builder builder = methodSignatureWithReturnType(waiterMethodName, opModel)
            .addParameter(requestType, opModel.getInput().getVariableName())
            .addModifiers(PUBLIC)
            .addAnnotation(Override.class)
            .addStatement("return $L.$L(() -> client.$N($N))",
                          waiterFieldName(waiterMethodName),
                          waiterClassName.simpleName().equals("Waiter") ? "run" : "runAsync",
                          lowercaseFirstChar(waiterDefinition.getValue().getOperation()),
                          opModel.getInput().getVariableName());

        return builder.build();
    }

    private List<MethodSpec> waiterAcceptorInitializers() {
        List<MethodSpec> initializers = new ArrayList<>();
        waiters.forEach((k, v) -> initializers.add(acceptorInitializer(k, v)));
        return initializers;
    }

    private MethodSpec acceptorInitializer(String waiterKey, WaiterDefinition waiterDefinition) {
        MethodSpec.Builder acceptorsMethod =
            MethodSpec.methodBuilder(waiterFieldName(waiterKey) + "Acceptors")
                      .addModifiers(PRIVATE, STATIC)
                      .returns(waiterAcceptorTypeName(waiterDefinition));

        acceptorsMethod.addStatement("$T result = new $T<>()", waiterAcceptorTypeName(waiterDefinition), ArrayList.class);

        for (Acceptor acceptor : waiterDefinition.getAcceptors()) {
            acceptorsMethod.addCode("result.add(")
                           .addCode(acceptor(acceptor))
                           .addCode(");");
        }

        acceptorsMethod.addStatement("result.addAll($T.DEFAULT_ACCEPTORS)", WaitersRuntime.class);

        acceptorsMethod.addStatement("return result");

        return acceptorsMethod.build();
    }

    protected String waiterFieldName(String waiterKey) {
        return lowercaseFirstChar(waiterKey) + "Waiter";
    }

    private OperationModel operationModel(WaiterDefinition waiterDefinition) {
        return model.getOperation(waiterDefinition.getOperation());
    }

    private MethodSpec.Builder methodSignatureWithReturnType(String waiterMethodName, OperationModel opModel) {
        return MethodSpec.methodBuilder(getWaiterMethodName(waiterMethodName))
                         .returns(getWaiterResponseType(opModel));
    }

    private String getWaiterMethodName(String waiterMethodName) {
        return "waitUntil" + waiterMethodName;
    }

    private TypeName waiterAcceptorTypeName(WaiterDefinition waiterDefinition) {
        WildcardTypeName wildcardTypeName = WildcardTypeName.supertypeOf(fullyQualifiedResponseType(waiterDefinition));

        return ParameterizedTypeName.get(ClassName.get(List.class),
                                         ParameterizedTypeName.get(ClassName.get(WaiterAcceptor.class), wildcardTypeName));
    }

    private TypeName fullyQualifiedResponseType(WaiterDefinition waiterDefinition) {
        String modelPackage = model.getMetadata().getFullModelPackageName();
        String operationResponseType = model.getOperation(waiterDefinition.getOperation()).getReturnType().getReturnType();
        return ClassName.get(modelPackage, operationResponseType);
    }

    private CodeBlock acceptor(Acceptor acceptor) {
        CodeBlock.Builder result = CodeBlock.builder();

        switch (acceptor.getState()) {
            case "success":
                result.add("$T.success", WaiterAcceptor.class);
                break;
            case "failure":
                result.add("$T.error", WaiterAcceptor.class);
                break;
            case "retry":
                result.add("$T.retry", WaiterAcceptor.class);
                break;
            default:
                throw new IllegalArgumentException("Unsupported acceptor state: " + acceptor.getState());
        }

        switch (acceptor.getMatcher()) {
            case "path":
                result.add("OnResponseAcceptor(")
                      .add(pathAcceptorBody(acceptor))
                      .add(")");
                break;
            case "pathAll":
                result.add("OnResponseAcceptor(")
                      .add(pathAllAcceptorBody(acceptor))
                      .add(")");
                break;
            case "pathAny":
                result.add("OnResponseAcceptor(")
                      .add(pathAnyAcceptorBody(acceptor))
                      .add(")");
                break;
            case "status":
                // Note: Ignores the result we've built so far because this uses a special acceptor implementation.
                int expected = Integer.parseInt(acceptor.getExpected().asText());
                return CodeBlock.of("new $T($L, $T.$L)", WaitersRuntime.ResponseStatusAcceptor.class, expected,
                                    WaiterState.class, waiterState(acceptor));
            case "error":
                result.add("OnExceptionAcceptor(")
                      .add(errorAcceptorBody(acceptor))
                      .add(")");
                break;
            default:
                throw new IllegalArgumentException("Unsupported acceptor matcher: " + acceptor.getMatcher());
        }

        return result.build();
    }

    private String waiterState(Acceptor acceptor) {
        switch (acceptor.getState()) {
            case "success":
                return WaiterState.SUCCESS.name();
            case "failure":
                return WaiterState.FAILURE.name();
            case "retry":
                return WaiterState.RETRY.name();
            default:
                throw new IllegalArgumentException("Unsupported acceptor state: " + acceptor.getState());
        }
    }

    private CodeBlock pathAcceptorBody(Acceptor acceptor) {
        String expected = acceptor.getExpected().asText();
        String expectedType = acceptor.getExpected() instanceof JrsString ? "$S" : "$L";
        return CodeBlock.builder()
                        .add("response -> {")
                        .add("$1T input = new $1T(response);", WaitersRuntime.Value.class)
                        .add("return $T.equals(", Objects.class)
                        .add(JmesPathInterpreter.interpret(acceptor.getArgument(), "input"))
                        .add(".value(), " + expectedType + ");", expected)
                        .add("}")
                        .build();
    }

    private CodeBlock pathAllAcceptorBody(Acceptor acceptor) {
        String expected = acceptor.getExpected().asText();
        String expectedType = acceptor.getExpected() instanceof JrsString ? "$S" : "$L";
        return CodeBlock.builder()
                        .add("response -> {")
                        .add("$1T input = new $1T(response);", WaitersRuntime.Value.class)
                        .add("$T<$T> resultValues = ", List.class, Object.class)
                        .add(JmesPathInterpreter.interpret(acceptor.getArgument(), "input"))
                        .add(".values();")
                        .add("return !resultValues.isEmpty() && "
                             + "resultValues.stream().allMatch(v -> $T.equals(v, " + expectedType + "));",
                             Objects.class, expected)
                        .add("}")
                        .build();
    }

    private CodeBlock pathAnyAcceptorBody(Acceptor acceptor) {
        String expected = acceptor.getExpected().asText();
        String expectedType = acceptor.getExpected() instanceof JrsString ? "$S" : "$L";
        return CodeBlock.builder()
                        .add("response -> {")
                        .add("$1T input = new $1T(response);", WaitersRuntime.Value.class)
                        .add("$T<$T> resultValues = ", List.class, Object.class)
                        .add(JmesPathInterpreter.interpret(acceptor.getArgument(), "input"))
                        .add(".values();")
                        .add("return !resultValues.isEmpty() && "
                             + "resultValues.stream().anyMatch(v -> $T.equals(v, " + expectedType + "));",
                             Objects.class, expected)
                        .add("}")
                        .build();
    }

    private CodeBlock errorAcceptorBody(Acceptor acceptor) {
        String expected = acceptor.getExpected().asText();
        String expectedType = acceptor.getExpected() instanceof JrsString ? "$S" : "$L";
        return CodeBlock.of("error -> $T.equals(errorCode(error), " + expectedType + ")", Objects.class, expected);
    }

    private MethodSpec staticErrorCodeMethod() {
        return MethodSpec.methodBuilder("errorCode")
                         .addModifiers(PRIVATE, STATIC)
                         .returns(String.class)
                         .addParameter(Throwable.class, "error")
                         .addCode("if (error instanceof $T) {", AwsServiceException.class)
                         .addCode("return (($T) error).awsErrorDetails().errorCode();", AwsServiceException.class)
                         .addCode("}")
                         .addCode("return null;")
                         .build();
    }
}
