package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;

/**
 * Lists the test cases of the test classes of a tests repository, so instructors can assign single test cases to a security policy.
 * <p>
 * Like {@link AresTestSources}, it reads the sources without compiling them: comments and literals are masked first, then every method annotated with a
 * JUnit or Ares test annotation is a test case. The annotations between the test annotation and the method name, such as {@code @Timeout(5)} or
 * {@code @DisplayName("...")}, are skipped together with their arguments.
 */
final class AresTestCases {

    /**
     * The annotations that make a method a test case: JUnit Jupiter, the Ares markers and jqwik, by their simple name, with the package each one is declared
     * in.
     */
    private static final Map<String, String> TEST_ANNOTATION_PACKAGES = Map.of("Test", "org.junit.jupiter.api", "RepeatedTest", "org.junit.jupiter.api", "TestFactory",
            "org.junit.jupiter.api", "TestTemplate", "org.junit.jupiter.api", "ParameterizedTest", "org.junit.jupiter.params", "PublicTest", "de.tum.cit.ase.ares.api.jupiter",
            "HiddenTest", "de.tum.cit.ase.ares.api.jupiter", "Property", "net.jqwik.api", "Example", "net.jqwik.api");

    /** The simple names of the test annotations as alternatives of a regular expression. */
    private static final String TEST_ANNOTATION_NAMES = String.join("|", TEST_ANNOTATION_PACKAGES.keySet());

    /**
     * A use of an annotation with the simple name of a test annotation, group 1 being its qualifier and group 2 its simple name. The qualifier repeats
     * possessively, so long qualified names do not grow the stack; giving back a qualifier part could only match a nested type such as {@code @Test.Inner},
     * which is no test annotation. The name must end after the test annotation, so {@code @Test$Tag} is no test annotation either.
     */
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@\\s*((?:[\\w$]+\\s*\\.\\s*)*+)(" + TEST_ANNOTATION_NAMES + ")(?!\\p{javaJavaIdentifierPart})");

    /** The name of a type variable, group 1, followed by its bound or by the separator, which stays unconsumed. */
    private static final String TYPE_VARIABLE_NAME = "(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\s*(?=extends(?!\\p{javaJavaIdentifierPart})|[,>&])";

    /** The annotations of a type variable, e.g. {@code @Marker} or {@code @Size(max = 3)}, which precede its name. */
    private static final String TYPE_VARIABLE_ANNOTATIONS = "(?:@\\s*[\\w$]+(?:\\s*\\.\\s*[\\w$]+)*+\\s*(?:\\([^()]*\\))?\\s*)*+";

    /**
     * A name in angle brackets that may declare a type variable, e.g. {@code Date} in {@code <X, @Marker Date extends java.sql.Date>}, group 1 being the
     * name. Its annotations are skipped, and the separator after the name stays unconsumed, so the next name after a comma is found as well. It is only
     * searched in the headers of types and methods, where it also matches a type argument such as {@code String} in a return type {@code List<String>}, which
     * only makes the check more cautious.
     */
    private static final Pattern TYPE_VARIABLE = Pattern.compile("[<,]\\s*" + TYPE_VARIABLE_ANNOTATIONS + TYPE_VARIABLE_NAME);

    /** A single-type import, group 1 being the imported type; a static import names a member and is no type import. */
    private static final Pattern SINGLE_TYPE_IMPORT = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}.])import\\s+(?!static\\s)([\\p{javaJavaIdentifierPart}.\\s]+?)\\s*;");

    /** The name of any annotation, also a qualified one. Its parts repeat possessively, so long qualified names do not grow the stack. */
    private static final Pattern ANNOTATION_NAME = Pattern.compile("@\\s*[\\w$]+(?:\\s*\\.\\s*[\\w$]+)*+");

    /** A complete Java identifier, also with letters outside ASCII, that is followed by an opening parenthesis. */
    private static final Pattern IDENTIFIER_BEFORE_PARENTHESIS = Pattern
            .compile("(?<!\\p{javaJavaIdentifierPart})(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)\\s*\\(");

    /** The start of a nested type declaration. A record names its components in parentheses after its name, which would otherwise look like a method. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:class|interface|enum|record)\\s+\\p{javaJavaIdentifierStart}");

    private static final Set<String> NOT_METHOD_NAMES = Set.of("if", "for", "while", "switch", "catch", "synchronized", "return", "new");

    /** A modifier that lets a subclass in another package override a method. Masked comments and literals cannot contain it. */
    private static final Pattern VISIBLE_IN_OTHER_PACKAGES = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:public|protected)(?!\\p{javaJavaIdentifierPart})");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Set<String> PRIMITIVE_TYPES = Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double");

    /** Marks a simple type name in a {@link TestSignature} that may refer to a type of the package, of {@code java.lang} or of a wildcard import. */
    private static final char UNRESOLVED_TYPE = '?';

    /** The modifier {@code final} of a parameter. */
    private static final Pattern FINAL_MODIFIER = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})final(?!\\p{javaJavaIdentifierPart})");

    /** The name of a parameter at the end of its declaration, with the brackets of an array declared after the name, e.g. {@code values[]}. */
    private static final Pattern PARAMETER_NAME = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\s*((?:\\[\\s*])*)\\s*$");

    private AresTestCases() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param javaSources the Java sources of the repository by their path relative to its root
     * @return the test classes with their test cases, and the other top-level classes of the sources as candidates for test code, sorted by name
     */
    static SecurityTestStructureDTO structure(Map<String, String> javaSources) {
        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(javaSources);
        Set<String> testClassNames = SecurityTestAssignments.testClassPaths(javaSources).keySet();
        Map<String, SecurityTestStructureDTO.TestClassDTO> classes = new TreeMap<>();
        for (Map.Entry<String, String> source : javaSources.entrySet()) {
            for (AresTestSources.TypeDeclaration type : AresTestSources.analyze(source.getKey(), source.getValue()).topLevelTypes()) {
                String name = type.qualifiedName();
                SecurityTestAssignments.TestClass testClass = testClasses.get(name);
                if (testClass != null) {
                    classes.put(name,
                            new SecurityTestStructureDTO.TestClassDTO(name, testClass.testCases(), true, !testClass.active(),
                                    testClass.testCases().stream().filter(testClass.switchedOff()::contains).toList(),
                                    testClass.testCases().stream().filter(testClass.overloaded()::contains).toList()));
                }
                else if (!testClassNames.contains(name)) {
                    classes.putIfAbsent(name, new SecurityTestStructureDTO.TestClassDTO(name, List.of(), false, false, List.of(), List.of()));
                }
            }
        }
        return new SecurityTestStructureDTO(List.copyOf(classes.values()));
    }

    /**
     * @param source the content of a Java file
     * @return the names of its test methods in the order of the source, each once
     */
    static List<String> testCases(String source) {
        return new ArrayList<>(testAnnotationOffsets(source).keySet());
    }

    /**
     * @param source the content of a Java file
     * @return for each test method, in the order of the source, the offset of its first test annotation; a new annotation of the method goes before it
     */
    static Map<String, Integer> testAnnotationOffsets(String source) {
        return testMethods(source).offsets();
    }

    /**
     * @param source the content of a Java file
     * @return the names of the test cases that more than one test method has, e.g. {@code @Test void check()} and {@code @ParameterizedTest void check(int)}:
     *         an entry {@code Class#check} cannot name one of them, so they cannot be assigned one by one
     */
    static Set<String> overloadedTestCases(String source) {
        return testMethods(source).overloaded();
    }

    /**
     * @param source the content of a Java file
     * @return the offsets of the names of its test method declarations whose name no other test method has, so that an annotation is matched to the
     *         declaration it is on: not to a name that a method that is no test may share with a test, and not to a name that several test methods share,
     *         because an entry {@code Class#method} could not tell them apart
     */
    static Set<Integer> uniqueTestDeclarationOffsets(String source) {
        Set<String> overloaded = testMethods(source).overloaded();
        Set<Integer> offsets = new HashSet<>();
        testSignatures(AresPolicyAnnotations.mask(source)).forEach((offset, signature) -> {
            if (!overloaded.contains(signature.name())) {
                offsets.add(offset);
            }
        });
        return offsets;
    }

    /**
     * What decides whether a method of a subclass overrides a test method: the name and the erased types of its parameters, and its visibility.
     *
     * @param name                   the name of the method
     * @param text                   the name with the erased parameter types, e.g. {@code check(int,java.util.List,?String[])}: qualified where the
     *                                   source writes or imports them qualified, and marked with {@code ?} where the simple name may refer to a type of
     *                                   the package, of {@code java.lang}, of a wildcard import or of the file itself
     * @param visibleInOtherPackages whether it is public or protected, so that a subclass in another package overrides it, too
     */
    record TestSignature(String name, String text, boolean visibleInOtherPackages) {

        /**
         * @return whether a parameter type is a simple name that the file does not resolve itself, so the same text in another file, also of the same
         *         package, may name another type, e.g. through another wildcard import
         */
        boolean dependsOnPackage() {
            return text.indexOf(UNRESOLVED_TYPE) >= 0;
        }
    }

    /**
     * @param masked the masked content of a Java file
     * @return the signatures of its test method declarations, by the offset of their names
     */
    static Map<Integer, TestSignature> testSignatures(String masked) {
        Map<Integer, TestSignature> signatures = new HashMap<>();
        Map<String, String> imported = importedTypes(masked);
        // The type variables of the types the file declares are in scope in their methods; a type argument elsewhere, e.g. of a field, declares none.
        Set<String> typeVariablesOfTypes = new HashSet<>();
        Matcher type = AresPolicyAnnotations.TYPE_DECLARATION.matcher(masked);
        while (type.find()) {
            int body = masked.indexOf('{', type.end());
            typeVariablesOfTypes.addAll(typeVariables(masked.substring(type.end(), body < 0 ? masked.length() : body)));
        }
        for (MatchResult annotation : testAnnotations(masked)) {
            MatchResult method = methodAfter(masked, annotation.end());
            if (method != null && !signatures.containsKey(method.start(1))) {
                int close = AresPolicyAnnotations.matchingParenthesis(masked, method.end() - 1);
                String parameters = masked.substring(method.end(), close < 0 ? masked.length() : close);
                // The modifiers precede the name, also before the annotations; the previous member ends with a semicolon or a brace outside them.
                int memberStart = memberStart(masked, annotation.start());
                boolean visible = VISIBLE_IN_OTHER_PACKAGES.matcher(masked.substring(memberStart, method.start(1))).find();
                int declaration = method.start(1);
                // The type parameters of the method itself precede its return type.
                Set<String> typeVariables = new HashSet<>(typeVariablesOfTypes);
                typeVariables.addAll(typeVariables(masked.substring(memberStart, declaration)));
                UnaryOperator<String> resolve = simpleName -> importedType(masked, simpleName, declaration, imported, typeVariables);
                String text = method.group(1) + "(" + parameterTypes(parameters, resolve) + ")";
                signatures.put(method.start(1), new TestSignature(method.group(1), text, visible));
            }
        }
        return signatures;
    }

    /**
     * @return the offset after the semicolon or brace that ends the member before the offset; a brace within the arguments of an annotation, e.g.
     *         {@code @ValueSource(ints = { 1 })}, ends no member
     */
    private static int memberStart(String masked, int offset) {
        int parentheses = 0;
        for (int position = offset - 1; position >= 0; position--) {
            char character = masked.charAt(position);
            if (character == ')') {
                parentheses++;
            }
            else if (character == '(') {
                parentheses--;
            }
            else if (parentheses <= 0 && (character == ';' || character == '{' || character == '}')) {
                return position + 1;
            }
        }
        return 0;
    }

    /**
     * The uses of test annotations in a source. A qualified name must name the package of the annotation, e.g. {@code @org.junit.jupiter.api.Test}, so a
     * marker such as {@code @example.Test} is no test annotation. A simple name refers to the test annotation unless the file declares a type of that name
     * where it is used or imports one from another package; which types a wildcard import provides is unknown, so it is assumed to be the test annotation.
     *
     * @param masked the masked content of a Java file
     * @return the matches of {@link #TEST_ANNOTATION} that are test annotations, in the order of the source
     */
    private static List<MatchResult> testAnnotations(String masked) {
        Map<String, String> imported = importedTypes(masked);
        List<MatchResult> annotations = new ArrayList<>();
        Matcher annotation = TEST_ANNOTATION.matcher(masked);
        while (annotation.find()) {
            String name = annotation.group(2);
            String qualifier = WHITESPACE.matcher(annotation.group(1)).replaceAll("");
            String expected = TEST_ANNOTATION_PACKAGES.get(name) + "." + name;
            boolean resolves;
            if (qualifier.isEmpty()) {
                resolves = !AresPolicyAnnotations.isShadowedAt(masked, name, annotation.start()) && imported.getOrDefault(name, expected).equals(expected);
            }
            else {
                resolves = (qualifier + name).equals(expected);
            }
            if (resolves) {
                annotations.add(annotation.toMatchResult());
            }
        }
        return annotations;
    }

    /**
     * @param header the masked header of a type or a method, up to its body or its name
     * @return the names it may declare as type variables, see {@link #TYPE_VARIABLE}
     */
    private static Set<String> typeVariables(String header) {
        Set<String> names = new HashSet<>();
        Matcher typeVariable = TYPE_VARIABLE.matcher(header);
        while (typeVariable.find()) {
            names.add(typeVariable.group(1));
        }
        return names;
    }

    /**
     * @return the type a single-type import gives the simple name where the method at the offset is declared; null if a type the file declares there, e.g. a
     *         member type, or a type variable shadows the import, or if there is no import
     */
    private static @Nullable String importedType(String masked, String simpleName, int offset, Map<String, String> imported, Set<String> typeVariables) {
        if (typeVariables.contains(simpleName) || AresPolicyAnnotations.isShadowedAt(masked, simpleName, offset)) {
            return null;
        }
        return imported.get(simpleName);
    }

    /**
     * @return the types the single-type imports of the masked source name, by their simple names
     */
    private static Map<String, String> importedTypes(String masked) {
        Map<String, String> imported = new HashMap<>();
        Matcher imports = SINGLE_TYPE_IMPORT.matcher(masked);
        while (imports.find()) {
            String type = WHITESPACE.matcher(imports.group(1)).replaceAll("");
            imported.putIfAbsent(type.substring(type.lastIndexOf('.') + 1), type);
        }
        return imported;
    }

    /**
     * @param resolve the qualified name of a simple type name where the method is declared: the type of a single-type import unless a type of the file
     *                    shadows it; null if it is unknown
     * @return the erased types of the parameters, separated by commas, see {@link TestSignature#text()}; a variable arity parameter is an array
     */
    private static String parameterTypes(String parameters, UnaryOperator<String> resolve) {
        List<String> types = new ArrayList<>();
        StringBuilder parameter = new StringBuilder();
        int typeArguments = 0;
        int position = 0;
        while (position < parameters.length()) {
            char character = parameters.charAt(position);
            if (character == '@') {
                // An annotation of the parameter, with its arguments, which may contain commas.
                Matcher name = ANNOTATION_NAME.matcher(parameters).region(position, parameters.length());
                position = name.lookingAt() ? skipArguments(parameters, skipWhitespace(parameters, name.end())) : position + 1;
                continue;
            }
            if (character == '<') {
                typeArguments++;
            }
            else if (character == '>') {
                typeArguments--;
            }
            else if (character == ',' && typeArguments == 0) {
                types.add(erasedType(parameter.toString(), resolve));
                parameter.setLength(0);
            }
            else if (typeArguments == 0) {
                parameter.append(character);
            }
            position++;
        }
        if (!parameter.toString().isBlank()) {
            types.add(erasedType(parameter.toString(), resolve));
        }
        return String.join(",", types);
    }

    /**
     * @param parameter the declaration of a parameter without its annotations and type arguments, e.g. {@code final java.util.List values}
     * @param resolve   the qualified name of a simple type name where the method is declared, see {@link #parameterTypes(String, UnaryOperator)}
     * @return its type, with the brackets of an array, e.g. {@code java.util.List}, {@code int} or {@code ?String[]}, see {@link TestSignature#text()}
     */
    private static String erasedType(String parameter, UnaryOperator<String> resolve) {
        String declaration = FINAL_MODIFIER.matcher(parameter).replaceAll(" ").strip();
        Matcher name = PARAMETER_NAME.matcher(declaration);
        String dimensions = "";
        if (name.find()) {
            dimensions = WHITESPACE.matcher(name.group(1)).replaceAll("");
            declaration = declaration.substring(0, name.start());
        }
        String type = WHITESPACE.matcher(declaration).replaceAll("").replace("...", "[]") + dimensions;
        int arrayStart = type.indexOf('[');
        String baseType = arrayStart < 0 ? type : type.substring(0, arrayStart);
        String arrayDimensions = arrayStart < 0 ? "" : type.substring(arrayStart);
        int dot = baseType.indexOf('.');
        String firstName = dot < 0 ? baseType : baseType.substring(0, dot);
        // A name that starts with a package, by the Java naming conventions in lower case, is fully qualified; one that starts with a type, e.g.
        // Outer.Inner, depends on what that type resolves to in the file.
        if (PRIMITIVE_TYPES.contains(baseType) || (dot >= 0 && Character.isLowerCase(firstName.charAt(0)))) {
            return baseType + arrayDimensions;
        }
        String resolvedType = resolve.apply(firstName);
        return (resolvedType != null ? resolvedType + baseType.substring(firstName.length()) : UNRESOLVED_TYPE + baseType) + arrayDimensions;
    }

    /**
     * @return the offset of the name of the method that the annotation ending at the offset annotates; -1 if none follows before the next body or statement
     */
    static int methodNameOffsetAfter(String masked, int offset) {
        MatchResult method = methodAfter(masked, offset);
        return method == null ? -1 : method.start(1);
    }

    /**
     * The test methods of a source.
     *
     * @param offsets    for each name, the offset of the first test annotation of the first method with it
     * @param overloaded the names that more than one test method has
     */
    private record TestMethods(Map<String, Integer> offsets, Set<String> overloaded) {
    }

    private static TestMethods testMethods(String source) {
        String masked = AresPolicyAnnotations.mask(source);
        Map<String, Integer> offsets = new LinkedHashMap<>();
        Map<String, Integer> declarations = new HashMap<>();
        Set<String> overloaded = new LinkedHashSet<>();
        for (MatchResult annotation : testAnnotations(masked)) {
            MatchResult method = methodAfter(masked, annotation.end());
            if (method != null) {
                String name = method.group(1);
                offsets.putIfAbsent(name, annotation.start());
                // Two test annotations of one method find the same declaration; another declaration with the name is an overload.
                Integer declaration = declarations.putIfAbsent(name, method.start(1));
                if (declaration != null && declaration != method.start(1)) {
                    overloaded.add(name);
                }
            }
        }
        return new TestMethods(offsets, overloaded);
    }

    /**
     * @return the name of the method that the annotation ending at the offset annotates; null if none follows before the next body or statement
     */
    static @Nullable String methodNameAfter(String masked, int offset) {
        MatchResult method = methodAfter(masked, offset);
        return method == null ? null : method.group(1);
    }

    /**
     * @return the match of the name of the method that the annotation ending at the offset annotates, its group 1 being the name; null if none follows
     */
    private static @Nullable MatchResult methodAfter(String masked, int offset) {
        int position = skipArguments(masked, skipWhitespace(masked, offset));
        while (position < masked.length()) {
            char character = masked.charAt(position);
            if (character == '{' || character == ';' || character == '}' || character == '=') {
                return null;
            }
            if (character == '@') {
                Matcher name = ANNOTATION_NAME.matcher(masked).region(position, masked.length());
                if (!name.lookingAt()) {
                    return null;
                }
                position = skipArguments(masked, skipWhitespace(masked, name.end()));
                continue;
            }
            // The annotation is on a nested type, not on a method. Transparent bounds let the look-behinds see the character before the region, so no match
            // starts inside an identifier.
            if (TYPE_DECLARATION.matcher(masked).region(position, masked.length()).useTransparentBounds(true).lookingAt()) {
                return null;
            }
            Matcher method = IDENTIFIER_BEFORE_PARENTHESIS.matcher(masked).region(position, masked.length()).useTransparentBounds(true);
            if (method.lookingAt()) {
                return NOT_METHOD_NAMES.contains(method.group(1)) ? null : method.toMatchResult();
            }
            position++;
        }
        return null;
    }

    /**
     * @return the offset after the argument list that starts at the offset, or the offset itself if none starts there
     */
    private static int skipArguments(String masked, int offset) {
        if (offset >= masked.length() || masked.charAt(offset) != '(') {
            return offset;
        }
        int depth = 0;
        for (int position = offset; position < masked.length(); position++) {
            char character = masked.charAt(position);
            if (character == '(') {
                depth++;
            }
            else if (character == ')' && --depth == 0) {
                return position + 1;
            }
        }
        return masked.length();
    }

    private static int skipWhitespace(String text, int offset) {
        int position = offset;
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }
}
