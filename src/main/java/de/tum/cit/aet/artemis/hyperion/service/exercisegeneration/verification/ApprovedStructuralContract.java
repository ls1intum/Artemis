package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaConstructor;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;
import com.thoughtworks.qdox.model.JavaType;

/**
 * Typed structural contract parsed and validated at the SPEC gate.
 * <p>
 * The SPEC format already requires exact Java signatures in fenced {@code java} blocks. Parsing those blocks with the same QDox model as Artemis's structure-oracle generator
 * gives later verification an immutable authority whose owner, type kind, relationships and overloads never depend on agent-authored solution code.
 */
final class ApprovedStructuralContract {

    private static final Pattern JAVA_BLOCK = Pattern.compile("```java\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final Map<String, JavaClass> types;

    private ApprovedStructuralContract(Map<String, JavaClass> types) {
        this.types = Map.copyOf(types);
    }

    static ParseResult parse(String specification, Set<String> requiredTypes) {
        return parse(specification, requiredTypes, requiredTypes);
    }

    static ParseResult parse(String specification, Set<String> requiredTypes, Set<String> structurallyGradedTypes) {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        List<String> errors = new ArrayList<>();
        Matcher blocks = JAVA_BLOCK.matcher(section(specification, "## Public API"));
        int blockCount = 0;
        while (blocks.find()) {
            blockCount++;
            try {
                builder.addSource(new StringReader(normalizeSkeleton(blocks.group(1))));
            }
            catch (RuntimeException exception) {
                errors.add("Java Public API block " + blockCount + " is not parseable: " + singleLine(exception.getMessage()));
            }
        }
        if (blockCount == 0) {
            errors.add("## Public API needs fenced ```java blocks containing exact type and member signatures");
        }

        Map<String, JavaClass> parsed = new LinkedHashMap<>();
        for (JavaClass type : builder.getClasses()) {
            if (!requiredTypes.contains(type.getSimpleName())) {
                errors.add(type.getSimpleName() + " is declared in ## Public API but has no row in ## Design");
                continue;
            }
            JavaClass duplicate = parsed.putIfAbsent(type.getSimpleName(), type);
            if (duplicate != null) {
                errors.add(type.getSimpleName() + " is declared more than once in ## Public API");
            }
        }
        for (String expectedType : requiredTypes.stream().sorted().toList()) {
            JavaClass type = parsed.get(expectedType);
            if (type == null) {
                errors.add(expectedType + " needs one exact Java declaration in its ## Public API block");
                continue;
            }
            if (!type.isPublic()) {
                errors.add(expectedType + " must be declared public because students create it as a top-level graded type");
            }
            List<String> unsupportedTypeModifiers = type.getModifiers().stream().filter(modifier -> Set.of("final", "sealed", "non-sealed").contains(modifier)).toList();
            if (structurallyGradedTypes.contains(expectedType) && (type.isRecord() || !unsupportedTypeModifiers.isEmpty() || !type.getTypeParameters().isEmpty())) {
                errors.add(expectedType
                        + " uses a record, generic type declaration, or final/sealed type modifier that the structural grader cannot enforce exactly. Use a public class, "
                        + "interface, or enum with an explicit non-generic contract.");
            }
            boolean unsupportedExecutable = type.getMethods().stream().anyMatch(method -> method.isVarArgs() || !method.getTypeParameters().isEmpty())
                    || type.getConstructors().stream().anyMatch(constructor -> constructor.isVarArgs() || !constructor.getTypeParameters().isEmpty());
            if (structurallyGradedTypes.contains(expectedType) && unsupportedExecutable) {
                errors.add(expectedType + " uses a varargs or generic method/constructor that the structural grader cannot enforce exactly. Use explicit parameter types.");
            }
            List<String> privateMembers = new ArrayList<>();
            if (!type.isRecord()) {
                type.getMethods().stream().filter(JavaMethod::isPrivate).map(JavaMethod::getName).forEach(privateMembers::add);
                type.getConstructors().stream().filter(JavaConstructor::isPrivate).map(constructor -> expectedType).forEach(privateMembers::add);
                type.getFields().stream().filter(JavaField::isPrivate).map(JavaField::getName).forEach(privateMembers::add);
            }
            if (!privateMembers.isEmpty()) {
                errors.add(expectedType + " exposes private implementation details in ## Public API: " + privateMembers + ". Keep only contract-visible signatures.");
            }
            List<String> packagePrivateMembers = new ArrayList<>();
            if (!type.isRecord()) {
                type.getMethods().stream().filter(method -> !method.isPrivate() && !isContractVisible(method)).map(JavaMethod::getName).forEach(packagePrivateMembers::add);
                type.getConstructors().stream().filter(constructor -> !constructor.isPrivate() && !isContractVisible(constructor)).map(constructor -> expectedType)
                        .forEach(packagePrivateMembers::add);
                type.getFields().stream().filter(field -> !field.isPrivate() && !isContractVisible(field)).map(JavaField::getName).forEach(packagePrivateMembers::add);
            }
            if (!packagePrivateMembers.isEmpty()) {
                errors.add(expectedType + " contains package-private signatures in ## Public API: " + packagePrivateMembers
                        + ". Make contract members public/protected or remove implementation details.");
            }
        }
        return new ParseResult(new ApprovedStructuralContract(parsed), List.copyOf(errors));
    }

    String toOracle(String packageName, ObjectMapper mapper, Set<String> includedTypes) {
        ArrayNode oracle = mapper.createArrayNode();
        types.values().stream().filter(type -> includedTypes.contains(type.getSimpleName())).sorted((left, right) -> left.getSimpleName().compareTo(right.getSimpleName()))
                .forEach(type -> oracle.add(toJson(type, packageName, mapper)));
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(oracle);
        }
        catch (Exception exception) {
            throw new IllegalStateException("The approved structural contract could not be serialized", exception);
        }
    }

    String toOracle(String packageName, ObjectMapper mapper) {
        return toOracle(packageName, mapper, types.keySet());
    }

    Set<String> typeNames() {
        return types.keySet();
    }

    List<String> solutionSurfaceReasons(Map<String, String> solutionFiles) {
        return repositorySurfaceReasons(solutionFiles, types.keySet(), "solution");
    }

    List<String> templateSurfaceReasons(Map<String, String> templateFiles, Set<String> includedTypes) {
        return repositorySurfaceReasons(templateFiles, includedTypes, "template");
    }

    private List<String> repositorySurfaceReasons(Map<String, String> repositoryFiles, Set<String> includedTypes, String repository) {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        List<String> reasons = new ArrayList<>();
        if (repositoryFiles != null) {
            repositoryFiles.entrySet().stream().filter(entry -> entry.getKey().endsWith(".java")).forEach(entry -> {
                try {
                    builder.addSource(new StringReader(entry.getValue()));
                }
                catch (RuntimeException exception) {
                    reasons.add("the " + repository + " source " + entry.getKey() + " is not structurally parseable: " + singleLine(exception.getMessage()));
                }
            });
        }
        Map<String, JavaClass> actualTypes = new LinkedHashMap<>();
        builder.getClasses().stream().filter(type -> includedTypes.contains(type.getSimpleName())).forEach(type -> {
            JavaClass duplicate = actualTypes.putIfAbsent(type.getSimpleName(), type);
            if (duplicate != null) {
                reasons.add("the " + repository + " declares more than one type named " + type.getSimpleName() + "; the approved API owner is ambiguous");
            }
        });
        List<String> unexpectedPublicTypes = builder.getClasses().stream().filter(JavaClass::isPublic).map(JavaClass::getSimpleName).filter(type -> !types.containsKey(type))
                .sorted().toList();
        if (!unexpectedPublicTypes.isEmpty()) {
            reasons.add("the " + repository + " exposes public type(s) absent from the approved Design/Public API contract: " + unexpectedPublicTypes
                    + ". Keep implementation helpers package-private or declare intentional exercise types in the specification.");
        }
        for (Map.Entry<String, JavaClass> expected : types.entrySet()) {
            if (!includedTypes.contains(expected.getKey())) {
                continue;
            }
            JavaClass actual = actualTypes.get(expected.getKey());
            if (actual == null) {
                continue; // The existing ownership gate reports a missing solution type more directly.
            }
            Set<String> expectedSurface = canonicalSurface(expected.getValue(), types.keySet());
            Set<String> actualSurface = canonicalSurface(actual, types.keySet());
            Set<String> missing = new LinkedHashSet<>(expectedSurface);
            missing.removeAll(actualSurface);
            Set<String> extra = new LinkedHashSet<>(actualSurface);
            extra.removeAll(expectedSurface);
            if (!missing.isEmpty() || !extra.isEmpty()) {
                reasons.add("the " + repository + " public API for " + expected.getKey() + " differs from the approved SPEC (missing " + missing + ", extra " + extra
                        + "). Remove invented overloads/public helpers and implement the exact approved type kind, relationships, and signatures; private helpers remain allowed.");
            }
        }
        return List.copyOf(reasons);
    }

    private static ObjectNode toJson(JavaClass type, String packageName, ObjectMapper mapper) {
        ObjectNode entry = mapper.createObjectNode();
        ObjectNode classNode = mapper.createObjectNode();
        classNode.put("name", type.getSimpleName());
        classNode.put("package", packageName);
        classNode.put("isInterface", type.isInterface());
        classNode.put("isEnum", type.isEnum());
        classNode.put("isAbstract", type.isAbstract() && !type.isInterface());
        JavaClass superclass = type.getSuperJavaClass();
        if (superclass != null && !"java.lang.Object".equals(superclass.getCanonicalName())) {
            classNode.put("superclass", superclass.getSimpleName());
        }
        if (!type.getInterfaces().isEmpty()) {
            classNode.set("interfaces", mapper.valueToTree(type.getInterfaces().stream().map(JavaClass::getSimpleName).sorted().toList()));
        }
        entry.set("class", classNode);

        ArrayNode methods = mapper.createArrayNode();
        type.getMethods().stream().filter(ApprovedStructuralContract::isContractVisible).forEach(method -> methods.add(methodJson(method, mapper)));
        putNonEmpty(entry, "methods", methods);
        ArrayNode attributes = mapper.createArrayNode();
        type.getFields().stream().filter(field -> !field.isEnumConstant()).filter(ApprovedStructuralContract::isContractVisible)
                .forEach(field -> attributes.add(fieldJson(field, mapper)));
        putNonEmpty(entry, "attributes", attributes);
        ArrayNode constructors = mapper.createArrayNode();
        type.getConstructors().stream().filter(ApprovedStructuralContract::isContractVisible).forEach(constructor -> constructors.add(constructorJson(constructor, mapper)));
        if (constructors.isEmpty() && !type.isInterface() && !type.isEnum()) {
            ObjectNode implicitConstructor = mapper.createObjectNode();
            implicitConstructor.set("modifiers", mapper.valueToTree(List.of("public")));
            constructors.add(implicitConstructor);
        }
        putNonEmpty(entry, "constructors", constructors);
        ArrayNode enumValues = mapper.createArrayNode();
        type.getFields().stream().filter(JavaField::isEnumConstant).map(JavaField::getName).forEach(enumValues::add);
        putNonEmpty(entry, "enumValues", enumValues);
        return entry;
    }

    private static Set<String> canonicalSurface(JavaClass type, Set<String> exerciseTypes) {
        String exercisePackage = type.getPackageName();
        Set<String> surface = new LinkedHashSet<>();
        surface.add("type:" + typeKind(type) + ":modifiers=" + type.getModifiers().stream().sorted().toList() + ":parameters="
                + type.getTypeParameters().stream().map(parameter -> canonicalTypeName(parameter.getGenericValue(), exerciseTypes, exercisePackage)).toList() + ":extends="
                + superclass(type) + ":implements="
                + type.getInterfaces().stream().map(interfaceType -> canonicalType(interfaceType, exerciseTypes, exercisePackage)).sorted().toList());
        type.getMethods().stream().filter(ApprovedStructuralContract::isContractVisible)
                .map(method -> "method:" + relevantModifiers(method.getModifiers(), method.getDeclaringClass().isInterface(), method.isDefault(), method.isStatic()) + ":"
                        + method.getTypeParameters().stream().map(parameter -> canonicalTypeName(parameter.getGenericValue(), exerciseTypes, exercisePackage)).toList() + ":"
                        + canonicalType(method.getReturnType(), exerciseTypes, exercisePackage) + ":" + method.getName()
                        + exactParameterTypes(method.getParameters(), exerciseTypes, exercisePackage) + ":throws="
                        + method.getExceptionTypes().stream().map(exception -> canonicalType(exception, exerciseTypes, exercisePackage)).sorted().toList())
                .forEach(surface::add);
        type.getFields().stream().filter(field -> !field.isEnumConstant()).filter(ApprovedStructuralContract::isContractVisible)
                .map(field -> "field:" + relevantModifiers(field.getModifiers(), field.getDeclaringClass().isInterface(), false, field.isStatic()) + ":"
                        + canonicalType(field.getType(), exerciseTypes, exercisePackage) + ":" + field.getName())
                .forEach(surface::add);
        List<JavaConstructor> visibleConstructors = type.getConstructors().stream().filter(ApprovedStructuralContract::isContractVisible).toList();
        visibleConstructors.stream()
                .map(constructor -> "constructor:" + relevantModifiers(constructor.getModifiers(), false, false, false)
                        + constructor.getTypeParameters().stream().map(parameter -> canonicalTypeName(parameter.getGenericValue(), exerciseTypes, exercisePackage)).toList()
                        + exactParameterTypes(constructor.getParameters(), exerciseTypes, exercisePackage) + ":throws="
                        + constructor.getExceptionTypes().stream().map(exception -> canonicalType(exception, exerciseTypes, exercisePackage)).sorted().toList())
                .forEach(surface::add);
        if (visibleConstructors.isEmpty() && !type.isInterface() && !type.isEnum()) {
            surface.add("constructor:[public][]");
        }
        type.getFields().stream().filter(JavaField::isEnumConstant).map(field -> "enum:" + field.getName()).forEach(surface::add);
        return Set.copyOf(surface);
    }

    private static String typeKind(JavaClass type) {
        if (type.isRecord()) {
            return "record";
        }
        if (type.isInterface()) {
            return "interface";
        }
        if (type.isEnum()) {
            return "enum";
        }
        return "class";
    }

    private static String superclass(JavaClass type) {
        JavaClass superclass = type.getSuperJavaClass();
        return superclass == null || "java.lang.Object".equals(superclass.getCanonicalName()) ? "" : superclass.getSimpleName();
    }

    private static List<String> relevantModifiers(List<String> declared, boolean interfaceOwner, boolean defaultMethod, boolean staticMethod) {
        return effectiveModifiers(declared, interfaceOwner, defaultMethod, staticMethod).stream()
                .filter(modifier -> Set.of("public", "protected", "static", "final", "abstract", "default").contains(modifier)).sorted().toList();
    }

    private static List<String> parameterTypes(List<JavaParameter> parameters) {
        return parameters.stream().map(parameter -> parameter.getType().getValue()).toList();
    }

    private static List<String> exactParameterTypes(List<JavaParameter> parameters, Set<String> exerciseTypes, String exercisePackage) {
        return parameters.stream().map(parameter -> canonicalType(parameter.getType(), exerciseTypes, exercisePackage) + (parameter.isVarArgs() ? "..." : "")).toList();
    }

    /**
     * QDox preserves the spelling used at each source site in {@code getGenericValue()}. The approved SPEC commonly uses a fully qualified JDK type while normal Java source
     * imports it, so comparing that spelling rejects a semantically identical API. Compare resolved generic names instead, while reducing exercise-owned types back to their
     * stable simple names because SPEC blocks intentionally have no package declaration.
     */
    private static String canonicalType(JavaType type, Set<String> exerciseTypes, String exercisePackage) {
        return canonicalTypeName(type.getGenericCanonicalName(), exerciseTypes, exercisePackage);
    }

    private static String canonicalTypeName(String typeName, Set<String> exerciseTypes, String exercisePackage) {
        String canonical = typeName;
        if (exercisePackage != null && !exercisePackage.isBlank()) {
            for (String exerciseType : exerciseTypes) {
                canonical = canonical.replaceAll("(?<![\\w$])" + Pattern.quote(exercisePackage + "." + exerciseType) + "(?![\\w$])", exerciseType);
            }
        }
        return canonical;
    }

    private static ObjectNode methodJson(JavaMethod method, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", method.getName());
        node.set("modifiers", mapper.valueToTree(effectiveModifiers(method.getModifiers(), method.getDeclaringClass().isInterface(), method.isDefault(), method.isStatic())));
        putParameters(node, method.getParameters(), mapper);
        node.put("returnType", simpleErasedType(method.getReturnType()));
        return node;
    }

    private static ObjectNode fieldJson(JavaField field, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("name", field.getName());
        node.set("modifiers", mapper.valueToTree(effectiveFieldModifiers(field)));
        node.put("type", simpleErasedType(field.getType()));
        return node;
    }

    private static ObjectNode constructorJson(JavaConstructor constructor, ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.set("modifiers", mapper.valueToTree(new ArrayList<>(constructor.getModifiers())));
        putParameters(node, constructor.getParameters(), mapper);
        return node;
    }

    private static void putParameters(ObjectNode node, List<JavaParameter> parameters, ObjectMapper mapper) {
        if (!parameters.isEmpty()) {
            node.set("parameters", mapper.valueToTree(parameters.stream().map(parameter -> simpleErasedType(parameter.getType())).toList()));
        }
    }

    /**
     * Ares's structural oracle schema uses erased simple Java names (for example {@code List}, not {@code java.util.List<String>}). Exact source-surface comparison separately
     * uses resolved canonical generic names; conflating the two representations makes ordinary imported collection APIs impossible to satisfy.
     */
    private static String simpleErasedType(JavaType type) {
        String name = type.getFullyQualifiedName();
        int array = name.indexOf('[');
        String suffix = array < 0 ? "" : name.substring(array);
        String component = array < 0 ? name : name.substring(0, array);
        int packageSeparator = component.lastIndexOf('.');
        return (packageSeparator < 0 ? component : component.substring(packageSeparator + 1)) + suffix;
    }

    private static List<String> effectiveModifiers(List<String> declared, boolean interfaceOwner, boolean defaultMethod, boolean staticMethod) {
        LinkedHashSet<String> modifiers = new LinkedHashSet<>(declared);
        if (interfaceOwner && !modifiers.contains("private")) {
            modifiers.add("public");
            if (!defaultMethod && !staticMethod) {
                modifiers.add("abstract");
            }
        }
        return List.copyOf(modifiers);
    }

    private static List<String> effectiveFieldModifiers(JavaField field) {
        LinkedHashSet<String> modifiers = new LinkedHashSet<>(field.getModifiers());
        if (field.getDeclaringClass().isInterface()) {
            modifiers.add("public");
            modifiers.add("static");
            modifiers.add("final");
        }
        return List.copyOf(modifiers);
    }

    private static boolean isContractVisible(JavaMethod method) {
        return method.isPublic() || method.isProtected() || method.getDeclaringClass().isInterface();
    }

    private static boolean isContractVisible(JavaConstructor constructor) {
        return constructor.isPublic() || constructor.isProtected();
    }

    private static boolean isContractVisible(JavaField field) {
        return field.isPublic() || field.isProtected() || field.getDeclaringClass().isInterface();
    }

    private static void putNonEmpty(ObjectNode parent, String field, ArrayNode value) {
        if (!value.isEmpty()) {
            parent.set(field, value);
        }
    }

    private static String normalizeSkeleton(String source) {
        return source.replaceAll("\\{\\s*\\.\\.\\.\\s*}", "{}");
    }

    private static String section(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int next = document.indexOf("\n## ", start + heading.length());
        return next < 0 ? document.substring(start) : document.substring(start, next);
    }

    private static String singleLine(String message) {
        return message == null ? "unknown parser error" : message.replace('\n', ' ').replace('\r', ' ').strip();
    }

    record ParseResult(ApprovedStructuralContract contract, List<String> errors) {

        boolean valid() {
            return errors.isEmpty();
        }
    }
}
