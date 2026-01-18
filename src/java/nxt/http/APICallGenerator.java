/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2026 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.http;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import nxt.Nxt;
import nxt.configuration.Setup;
import nxt.http.APIServlet.APIRequestHandler;
import nxt.http.callers.ApiSpec;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;

public class APICallGenerator {

    private static final Predicate<String> ENTITY_IDENTIFIERS = exactMatch("account", "recipient", "sender", "asset",
            "poll", "account", "currency", "order", "offer", "transaction", "ledgerId", "event", "goods", "buyer",
            "purchase", "holding", "block", "ecBlockId", "setter", "cancellingAccount"
    ).or(phasingParamWhich(endsWith("Holding")));

    private static final Predicate<String> CHAIN_IDENTIFIERS = exactMatch("chain", "exchange").or(contains("Chain"));

    private static final Predicate<String> INT_IDENTIFIERS = exactMatch("height", "fromHeight", "toHeight", "timestamp", "firstIndex", "lastIndex",
            "type", "subtype", "deadline", "ecBlockHeight", "totalPieces", "minimumPieces", "minParticipants", "childIndex",
            "startFromChildIndex", "decimals", "issuanceHeight", "expirationHeight", "controlMinDuration", "controlMaxDuration",
            "period", "finishHeight", "numberOfConfirmations", "registrationPeriod", "quantity", "deliveryDeadlineTimestamp",
            "atomicOrphanUnconfirmedPoolDeadline")
            .or(phasingParamWhich(endsWith("FinishHeight")));

    private static final Predicate<String> BYTE_IDENTIFIERS = exactMatch("holdingType", "algorithm", "minDifficulty",
            "maxDifficulty", "votingModel", "minNumberOfOptions", "maxNumberOfOptions", "minRangeValue", "maxRangeValue",
            "minBalanceModel", "participantCount")
            .or(phasingParamWhich(endsWith("VotingModel", "MinBalanceModel", "HashedSecretAlgorithm")));


    private static final Predicate<String> BOOLEAN_IDENTIFIERS = exactMatch("executedOnly", "phased", "broadcast",
            "voucher", "retrieve", "add", "remove", "validate", "countVotes", "apply")
            .or(startsWith("include", "is"))
            .or(contains("Is"));

    private static final Predicate<String> LONG_IDENTIFIERS = contains("NQT", "FQT", "FXT", "QNT")
            .or(phasingParamWhich(endsWith("Quorum", "MinBalance", "Holding")))
            .or(exactMatch("timeout", "counter", "minBalance", "amount"));

    private static final Predicate<String> BYTE_ARRAYS = startsWith("fullHash", "publicKey", "chainCode")
            .or(contains("FullHash", "PublicKey"))
            .or(endsWith("MessageData", "Nonce", "ransactionBytes"));

    private static final Predicate<String> REMOTE_ONLY_APIS = exactMatch("eventRegister", "eventWait");

    private static final String outputPackageName = "nxt.http.callers";

    private static final String defaultAddOns = "nxt.addons.ContractRunner;nxt.addons.StandbyShuffling;nxt.addons.TaxReportAddOn";

    private final String requestType;
    private final ClassName className;
    private final String typeName;
    private final TypeVariableName typeVariableName;
    private final TypeName parameterMethodReturnType;

    public APICallGenerator(String requestType) {
        this.requestType = requestType;
        this.typeName = initialCaps(requestType) + "Call";
        className = ClassName.get(outputPackageName, typeName);
        typeVariableName = null;
        parameterMethodReturnType = className;
    }

    public APICallGenerator(String requestType, String typeName) {
        this.requestType = requestType;
        this.typeName = typeName;
        className = ClassName.get(outputPackageName, typeName);
        typeVariableName = TypeVariableName.get("T", className);
        parameterMethodReturnType = typeVariableName;
    }

    public static void main(String[] args) {
        init();
        generateApiCallers();
        generateApiSpec();
    }

    private static void init() {
        Properties properties = new Properties();
        properties.put("nxt.adminPassword", "password");
        properties.put("nxt.addOns", defaultAddOns);
        // required by nxt.addons.TaxReportAddOn
        properties.put("nxt.ledgerTrimKeep", "0");
        properties.put("nxt.ledgerLogUnconfirmed", "0");

        Nxt.init(Setup.NOT_INITIALIZED, properties);
        APIServlet.initClass();
    }

    private static void generateApiCallers() {
        SuperClasses superClasses = creteSuperClasses();

        Map<String, APIRequestHandler> apiRequestHandlers = APIServlet.getAPIRequestHandlers();
        for (String requestType : apiRequestHandlers.keySet()) {
            APIRequestHandler apiRequestHandler = apiRequestHandlers.get(requestType);
            SuperClass superClass = superClasses.selectSuperClass(apiRequestHandler);
            new APICallGenerator(requestType).generateApiCall(apiRequestHandler, superClass);
        }
    }

    private static SuperClasses creteSuperClasses() {
        SuperClass noTransactionSuperClass = createAPICallBuilderSuperClass();

        SuperClass chainSpecificSuperClass = new APICallGenerator(null, "ChainSpecificCallBuilder")
                .createChainSpecificSuperClass(noTransactionSuperClass);

        SuperClass oneSideSuper = new APICallGenerator(null, "CreateOneSideTransactionCallBuilder")
                .generateCreateOneSideTransactionCallBuilder(chainSpecificSuperClass);

        SuperClass twoSidesSuper = new APICallGenerator(null, "CreateTwoSidesTransactionCallBuilder")
                .generateCreateTwoSidesTransactionCallBuilder(oneSideSuper);

        return new SuperClasses(noTransactionSuperClass, chainSpecificSuperClass, oneSideSuper, twoSidesSuper);
    }

    private SuperClass createChainSpecificSuperClass(SuperClass superClass) {
        return generateCallBuilder(superClass, Collections.singletonList("chain"), Collections.emptyList());
    }

    private static void generateApiSpec() {
        // Generate the API Specification enum
        ClassName enumClass = ClassName.get(outputPackageName, "ApiSpec");
        TypeSpec.Builder apiSpec = TypeSpec.enumBuilder(enumClass).addModifiers(Modifier.PUBLIC);
        for (Map.Entry<String, APIRequestHandler> entry : APIServlet.getAPIRequestHandlers().entrySet()) {
            String requestType = entry.getKey();
            APIRequestHandler apiRequestHandler = entry.getValue();
            List<String> fileParameters = apiRequestHandler.getFileParameters();
            CodeBlock.Builder codeBlockBuilder = CodeBlock.builder().add("$L", apiRequestHandler.isChainSpecific()).add(", ");
            if (!fileParameters.isEmpty()) {
                codeBlockBuilder.add("new String[]{\"$L\"}", String.join("\", \"", fileParameters));
            } else {
                codeBlockBuilder.add("$L", (Object) null);
            }
            codeBlockBuilder.add(", ").add("\"$L\"", String.join("\", \"", apiRequestHandler.getParameters()));
            apiSpec.addEnumConstant(requestType, TypeSpec.anonymousClassBuilder(codeBlockBuilder.build()).build());
        }

        // Generate fields and constructor
        apiSpec.addField(TypeName.BOOLEAN, "isChainSpecific", Modifier.PRIVATE, Modifier.FINAL);
        apiSpec.addField(ParameterizedTypeName.get(List.class, String.class), "fileParameters", Modifier.PRIVATE, Modifier.FINAL);
        apiSpec.addField(ParameterizedTypeName.get(List.class, String.class), "parameters", Modifier.PRIVATE, Modifier.FINAL);
        CodeBlock codeBlock = CodeBlock.builder().add("this.$L = $T.asList($L);", "parameters", Arrays.class, "parameters").build();
        apiSpec.addMethod(MethodSpec.constructorBuilder().
                addParameter(TypeName.BOOLEAN, "isChainSpecific").
                addParameter(TypeName.get(String[].class), "fileParameters").
                addParameter(TypeName.get(String[].class), "parameters").varargs().
                addStatement("this.$L = $L", "isChainSpecific", "isChainSpecific").
                addStatement("this.$L = $L == null ? $T.emptyList() : Arrays.asList($L)",
                        "fileParameters", "fileParameters", Collections.class, "fileParameters").
                addCode(codeBlock).
                build());

        // Generate getter
        apiSpec.addMethod(MethodSpec.methodBuilder("isChainSpecific")
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return isChainSpecific")
                .build());
        apiSpec.addMethod(MethodSpec.methodBuilder("getFileParameters")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addStatement("return fileParameters")
                .build());
        apiSpec.addMethod(MethodSpec.methodBuilder("getParameters")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addStatement("return parameters")
                .build());

        // Create source file
        writeToFile(apiSpec.build());
    }

    private void generateApiCall(APIRequestHandler apiRequestHandler, SuperClass superClass) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(typeName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(superClass.getTypeName(className));

        classBuilder.addMethod(createCallerConstructor());
        classBuilder.addMethods(createFactoryMethod(apiRequestHandler));

        classBuilder.addMethods(generateParameterHandlers(superClass, apiRequestHandler.getParameters(), apiRequestHandler.getFileParameters()));

        classBuilder.addMethods(createIsRemoteOnly());

        writeToFile(classBuilder.build());
    }

    private SuperClass generateCreateTwoSidesTransactionCallBuilder(SuperClass superClass) {
        return generateCallBuilder(superClass, CreateTransaction.getRecipientParameters(), CreateTransaction.getRecipientFileParameters());
    }

    private SuperClass generateCreateOneSideTransactionCallBuilder(SuperClass superClass) {
        return generateCallBuilder(superClass, CreateTransaction.getCommonParameters(), CreateTransaction.getCommonFileParameters());
    }

    private SuperClass generateCallBuilder(SuperClass superClass, List<String> parameters, List<String> fileParameters) {
        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(typeName)
                .addModifiers(Modifier.PUBLIC)
                .addTypeVariable(typeVariableName)
                .superclass(superClass.getTypeName(typeVariableName));

        classBuilder.addMethod(createBuilderConstructor());
        classBuilder.addMethods(generateParameterHandlers(superClass, parameters, fileParameters));

        writeToFile(classBuilder.build());
        return new SuperClass(superClass, className, parameters, fileParameters);
    }

    private List<MethodSpec> generateParameterHandlers(SuperClass superClass, List<String> parameters1, List<String> fileParameters1) {
        List<String> parameters = parameters1.stream()
                .filter(superClass.handledParameters().negate())
                .collect(Collectors.toList());

        Set<String> fileParameters = new LinkedHashSet<>(fileParameters1);
        final List<MethodSpec> classBuilder = new ArrayList<>(createParameterMethods(parameters, fileParameters));
        fileParameters.stream()
                .filter(superClass.handledParameters().negate())
                .forEach(fileParam -> classBuilder.addAll(createFileParameterMethods(fileParam)));

        return classBuilder;
    }

    private MethodSpec createBuilderConstructor() {
        return MethodSpec.constructorBuilder()
                .addParameter(ApiSpec.class, "apiSpec")
                .addModifiers(Modifier.PROTECTED)
                .addStatement("super(apiSpec)")
                .build();
    }

    private static void writeToFile(TypeSpec typeSpec) {
        JavaFile javaFile = JavaFile.builder(outputPackageName, typeSpec)
                .indent("    ")
                .addFileComment("Auto generated code, do not modify")
                .skipJavaLangImports(true)
                .build();
        Path directory = Paths.get("./src/java");
        try {
            javaFile.writeTo(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<MethodSpec> createIsRemoteOnly() {
        if (!REMOTE_ONLY_APIS.test(requestType)) {
            return Collections.emptyList();
        }
        MethodSpec remoteOnly = MethodSpec.methodBuilder("isRemoteOnly")
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addStatement("return true")
                .addAnnotation(Override.class)
                .build();
        return Collections.singletonList(remoteOnly);
    }

    private List<MethodSpec> createFileParameterMethods(String fileParam) {
        if (fileParam == null) {
            return Collections.emptyList();
        }
        MethodSpec setter = MethodSpec.methodBuilder(fileParam)
                .addModifiers(Modifier.PUBLIC)
                .returns(parameterMethodReturnType)
                .addParameter(byte[].class, "b")
                .addStatement("return parts($S, $L)", fileParam, "b").build();
        return Collections.singletonList(setter);
    }

    private List<MethodSpec> createParameterMethods(List<String> parameters, Set<String> fileParameters) {
        List<MethodSpec> result = new ArrayList<>();

        Map<String, Integer> paramMap = parameters.stream().collect(groupingBy(Function.identity(), summingInt(e -> 1)));
        for (String paramName : paramMap.keySet()) {
            boolean isVarargs = paramMap.get(paramName) > 1;
            if (ENTITY_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", String.class, isVarargs));
                result.add(createMethod(paramName, "unsignedLongParam", long.class, isVarargs));
            } else if (CHAIN_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", String.class, isVarargs));
                result.add(createMethod(paramName, "param", int.class, isVarargs));
            } else if (INT_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", int.class, isVarargs));
            } else if (BOOLEAN_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", boolean.class, isVarargs));
            } else if (BYTE_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", byte.class, isVarargs));
            } else if (LONG_IDENTIFIERS.test(paramName)) {
                result.add(createMethod(paramName, "param", long.class, isVarargs));
            } else {
                result.add(createMethod(paramName, "param", String.class, isVarargs));
            }
            if (BYTE_ARRAYS.test(paramName)) {
                result.add(createMethod(paramName, "param", byte[].class, isVarargs));
            }
            String fileParamName = paramName;
            if (fileParamName.endsWith("Data")) {
                fileParamName = fileParamName.substring(0, fileParamName.length() - 4);
            }
            fileParamName += "File";
            if (fileParameters.remove(fileParamName)) {
                result.addAll(createFileParameterMethods(fileParamName));
            }
        }
        return result;
    }

    private MethodSpec createCallerConstructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super($T." + requestType + ")", ApiSpec.class)
                .build();
    }

    private List<MethodSpec> createFactoryMethod(APIRequestHandler apiRequestHandler) {
        List<MethodSpec> result = new ArrayList<>();
        if (!(apiRequestHandler instanceof CreateTransaction)) {
            MethodSpec factory = MethodSpec.methodBuilder("create")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addStatement("return new $L()", typeName)
                    .returns(className)
                    .build();
            result.add(factory);
        }
        if (apiRequestHandler.isChainSpecific()) {
            MethodSpec factory = MethodSpec.methodBuilder("create")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(int.class, "chain")
                    .addStatement("return new $L().param($S, chain)", typeName, "chain")
                    .returns(className)
                    .build();
            result.add(factory);
        }
        return result;
    }

    private MethodSpec createMethod(String paramName, String paramMethodName, Class<?> paramMethodType, boolean isVarargs) {
        MethodSpec.Builder setterBuilder = MethodSpec.methodBuilder(paramName)
                .addModifiers(Modifier.PUBLIC)
                .returns(parameterMethodReturnType)
                .addStatement("return " + paramMethodName + "($S, $L)", paramName, paramName);
        if (isVarargs) {
            Class<?> arrayClass = Array.newInstance(paramMethodType, 0).getClass();
            setterBuilder.addParameter(arrayClass, paramName).varargs();
        } else {
            setterBuilder.addParameter(paramMethodType, paramName);
        }

        return setterBuilder.build();
    }

    private static String initialCaps(String requestType) {
        return requestType.substring(0, 1).toUpperCase() + requestType.substring(1);
    }

    private static Predicate<String> startsWith(String... strings) {
        return s -> Stream.of(strings).anyMatch(s::startsWith);
    }

    private static Predicate<String> endsWith(String... strings) {
        return s -> Stream.of(strings).anyMatch(s::endsWith);
    }

    private static Predicate<String> contains(String... strings) {
        return s -> Stream.of(strings).anyMatch(s::contains);
    }

    private static Predicate<String> exactMatch(String... strings) {
        return new HashSet<>(Arrays.asList(strings))::contains;
    }

    private static Predicate<String> phasingParamWhich(Predicate<String> additionalCondition) {
        return startsWith("phasing", "control").and(additionalCondition);
    }

    private static class SuperClasses {
        private final SuperClass noTransactionSuperClass;
        private final SuperClass chainSpecificSuperClass;
        private final SuperClass oneSideSuper;
        private final SuperClass twoSidesSuper;

        public SuperClasses(SuperClass noTransactionSuperClass, SuperClass chainSpecificSuperClass, SuperClass oneSideSuper, SuperClass twoSidesSuper) {
            this.noTransactionSuperClass = noTransactionSuperClass;
            this.chainSpecificSuperClass = chainSpecificSuperClass;
            this.oneSideSuper = oneSideSuper;
            this.twoSidesSuper = twoSidesSuper;
        }

        SuperClass selectSuperClass(APIRequestHandler apiRequestHandler) {
            if ((apiRequestHandler instanceof CreateTransaction)) {
                if (apiRequestHandler.canHaveRecipient()) {
                    return twoSidesSuper;
                }
                return oneSideSuper;
            }
            if (apiRequestHandler.isChainSpecific()) {
                return chainSpecificSuperClass;
            }
            return noTransactionSuperClass;
        }
    }

    private static class SuperClass {
        private final ClassName className;
        private final Set<String> handledParameters = new HashSet<>();

        private SuperClass(ClassName name) {
            className = name;
        }

        private SuperClass(ClassName name, Collection<String> handledParameters) {
            this(name);
            this.handledParameters.addAll(handledParameters);
        }

        private SuperClass(SuperClass superClass, ClassName name, Collection<String> handledParameters, Collection<String> handledFileParameters) {
            this(name, handledParameters);
            this.handledParameters.addAll(handledFileParameters);
            this.handledParameters.addAll(superClass.handledParameters);
        }

        public TypeName getTypeName(TypeName typeArgument) {
            return ParameterizedTypeName.get(className, typeArgument);
        }

        Predicate<String> handledParameters() {
            return handledParameters::contains;
        }
    }

    private static SuperClass createAPICallBuilderSuperClass() {
        return new SuperClass(
                ClassName.get(APICall.Builder.class),
                Arrays.asList("secretPhrase", "privateKey", "sharedPiece", "sharedPieceAccount")
        );
    }
}
