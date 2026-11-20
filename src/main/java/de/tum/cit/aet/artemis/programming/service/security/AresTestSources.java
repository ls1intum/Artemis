package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Reads how a Java source of the tests repository uses Ares: its top-level types, their {@code @Policy} annotations, whether they register the Ares
 * sandbox and whether they contain tests.
 * <p>
 * Like {@link AresPolicyAnnotations}, all searches run on the masked source, so comments and string literals never count. The analysis only covers the
 * layout the Security Framework can manage reliably: one relevant top-level type per file, whose own {@code @Policy} annotation decides its sandbox.
 * Everything else is reported as a {@link SecurityFrameworkState.UnsupportedReasonType}, so the caller neither reports a state for it nor changes it.
 */
final class AresTestSources {

    private static final String ARES_2_PACKAGE = "de.tum.cit.ase.ares.api";

    private static final String ARES_JUPITER_PACKAGE = ARES_2_PACKAGE + ".jupiter";

    private static final String ARES_1_PACKAGE = "de.tum.in.test.api";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\p{javaJavaIdentifierPart}.]+)\\s*;", Pattern.MULTILINE);

    /** A type declaration; the keyword must not follow a dot, so {@code Foo.class} is no declaration. */
    private static final Pattern TYPE_DECLARATION = AresPolicyAnnotations.TYPE_DECLARATION;

    private static final String END_OF_IDENTIFIER = AresPolicyAnnotations.END_OF_IDENTIFIER;

    private static final Pattern SUPERCLASS = Pattern.compile("^\\s*+(?:<[^{]*?>\\s*+)?extends\\s+([\\p{javaJavaIdentifierPart}.]+)");

    /** The modifier {@code private}, which keeps a member type from being inherited. Masked comments and literals cannot contain it. */
    private static final Pattern PRIVATE_MODIFIER = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})private(?!\\p{javaJavaIdentifierPart})");

    /** Separates the names in the clauses {@code extends} and {@code implements} of a type declaration. */
    private static final Pattern SUPERTYPE_SEPARATOR = Pattern.compile("[\\s,]+");

    private static final String JUNIT_API_PACKAGE = "org.junit.jupiter.api";

    private static final String JUNIT_PARAMS_PACKAGE = "org.junit.jupiter.params";

    /** The JUnit annotations that make a method a test, by their simple name, with their package. */
    private static final Map<String, String> JUNIT_TEST_MARKERS = Map.of("Test", JUNIT_API_PACKAGE, "RepeatedTest", JUNIT_API_PACKAGE, "TestFactory", JUNIT_API_PACKAGE,
            "TestTemplate", JUNIT_API_PACKAGE, "ParameterizedTest", JUNIT_PARAMS_PACKAGE);

    /** The uses of each JUnit test annotation, by its simple name, compiled once. */
    private static final Map<String, Pattern> JUNIT_TEST_MARKER_USES = JUNIT_TEST_MARKERS.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, marker -> Pattern.compile(annotationUse(marker.getValue(), marker.getKey()))));

    private static final String JUNIT_EXTENSION_PACKAGE = "org.junit.jupiter.api.extension";

    private static final String EXTEND_WITH_NAME = "ExtendWith";

    /** The uses of JUnit's {@code @ExtendWith}. */
    private static final Pattern EXTEND_WITH = Pattern.compile(annotationUse(JUNIT_EXTENSION_PACKAGE, EXTEND_WITH_NAME));

    private static final Pattern SINGLE_TYPE_IMPORT = Pattern.compile("\\bimport\\s+([\\p{javaJavaIdentifierPart}.]+)\\s*;");

    private static final List<String> ARES_CLASS_MARKERS = List.of("Public", "Hidden");

    private static final List<String> ARES_METHOD_MARKERS = List.of("PublicTest", "HiddenTest");

    /** An Ares annotation that registers the sandbox, but that the Security Framework does not manage. */
    private static final String UNSUPPORTED_ARES_MARKER = "JupiterAresTest";

    private static final String SECURITY_EXTENSION = "JupiterSecurityExtension";

    /** The {@code .class} after a type name. */
    private static final String CLASS_LITERAL = "\\s*\\.\\s*class" + END_OF_IDENTIFIER;

    /** The security extension registered directly, e.g. in {@code @ExtendWith(JupiterSecurityExtension.class)}, with or without its package. */
    private static final Pattern SECURITY_EXTENSION_USE = Pattern.compile("(" + Pattern.quote(ARES_JUPITER_PACKAGE + ".") + ")?" + SECURITY_EXTENSION + CLASS_LITERAL);

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private static final Pattern WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+([\\p{javaJavaIdentifierPart}.]+)\\.\\*\\s*;");

    private static final Pattern ARES_JUPITER_WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+" + Pattern.quote(ARES_JUPITER_PACKAGE) + "\\.\\*\\s*;");

    /** The lower-case subpackages and then the type or the {@code *} after {@link #ARES_2_PACKAGE} in an {@link #ARES_2_REFERENCE}. */
    private static final String ARES_2_NAMES_AFTER_PACKAGE = "((?:\\s*\\.\\s*[a-z_]\\p{javaJavaIdentifierPart}*)*\\s*\\.\\s*(?:[A-Z]\\p{javaJavaIdentifierPart}*|\\*))";

    /**
     * A reference to an Ares 2 type or package in an import, an annotation or the code: the package, any lower-case subpackages and then a type or the
     * {@code *} of a wildcard import. A static import ends at the type, so its member is not part of the match.
     */
    private static final Pattern ARES_2_REFERENCE = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}.])" + Pattern.quote(ARES_2_PACKAGE) + ARES_2_NAMES_AFTER_PACKAGE);

    /** The JUnit 5 subpackage of Ares, relative to {@link #ARES_2_PACKAGE}, as a prefix of the API names in {@link #SANDBOX_APIS}. */
    private static final String JUPITER = "jupiter.";

    /**
     * The parts of Ares the Security Framework itself switches on and off: the policy annotation and the markers and extension that register the sandbox,
     * relative to {@link #ARES_2_PACKAGE}. Tests that use only these keep working without the Ares library once the sandbox is off.
     */
    private static final Set<String> SANDBOX_APIS = Stream.concat(Stream.of("Policy", JUPITER + "*", JUPITER + SECURITY_EXTENSION, JUPITER + UNSUPPORTED_ARES_MARKER),
            Stream.concat(ARES_CLASS_MARKERS.stream(), ARES_METHOD_MARKERS.stream()).map(marker -> JUPITER + marker)).collect(Collectors.toUnmodifiableSet());

    /** The {@code @} of an Ares marker and its optional package. */
    private static final String MARKER_PREFIX = AresPolicyAnnotations.ANNOTATION_START + "(" + AresPolicyAnnotations.packagePrefix(ARES_JUPITER_PACKAGE) + ")?";

    /** The uses of each Ares marker, with or without its package, compiled once; a name ends where a Java identifier ends, so {@code @Public$Tag} is no {@code @Public}. */
    private static final Map<String, Pattern> MARKER_USES = Stream.of(ARES_CLASS_MARKERS.stream(), ARES_METHOD_MARKERS.stream(), Stream.of(UNSUPPORTED_ARES_MARKER))
            .flatMap(Function.identity()).collect(Collectors.toUnmodifiableMap(Function.identity(), marker -> Pattern.compile(MARKER_PREFIX + marker + END_OF_IDENTIFIER)));

    /**
     * A top-level type of a source file.
     *
     * @param simpleName       the name as declared
     * @param qualifiedName    the name including the package of the file
     * @param superclass       the fully qualified names the class it extends may have: the one its single-type import or qualified name gives, or else the
     *                             one in its own package and one per wildcard import; empty if it extends nothing
     * @param supertypes       the fully qualified names each type it extends or implements may have, resolved like the superclass, which they include
     * @param policies         its own Ares {@code @Policy} annotations
     * @param methodPolicies   the Ares {@code @Policy} annotations of its methods, by the name of the method; Ares applies such an annotation instead of the
     *                             one of the class to that test case
     * @param registersSandbox whether it registers the Ares sandbox through {@code @Public} or {@code @Hidden} on the type or {@code @PublicTest} or
     *                             {@code @HiddenTest} on a method
     * @param containsTests    whether one of its methods is a JUnit or Ares test
     * @param testSignatures   the signatures of its test methods, in the order of the source, which decide whether a subclass overrides one of them
     * @param memberTypes      the types declared directly in its body that its subclasses may inherit as member types, i.e. that are not private
     */
    record TypeDeclaration(String simpleName, String qualifiedName, List<String> superclass, List<List<String>> supertypes, List<AresPolicyAnnotations.Annotation> policies,
            Map<String, AresPolicyAnnotations.Annotation> methodPolicies, boolean registersSandbox, boolean containsTests, List<AresTestCases.TestSignature> testSignatures,
            Set<MemberType> memberTypes) {

        /**
         * @return the package of the type; empty for the default package
         */
        String packageName() {
            return qualifiedName.length() > simpleName.length() ? qualifiedName.substring(0, qualifiedName.length() - simpleName.length() - 1) : "";
        }

        /**
         * @return whether the type takes part in the sandbox decision: it is a test class or carries a {@code @Policy}
         */
        boolean isRelevant() {
            return containsTests || registersSandbox || !policies.isEmpty();
        }
    }

    /**
     * The analysis of one source file.
     *
     * @param path          the path of the file relative to the repository root
     * @param topLevelTypes its top-level types, in their order
     * @param problems      the parts of the file the Security Framework cannot manage
     * @param usesAres1     whether the file refers to Ares 1, whose own sandbox runs independently of the Security Framework
     * @param aresApis      the other parts of Ares 2 the file uses, see {@link #aresApis(String)}
     */
    record Summary(String path, List<TypeDeclaration> topLevelTypes, List<SecurityFrameworkState.UnsupportedReasonType> problems, boolean usesAres1, List<String> aresApis) {
    }

    /**
     * A member type that a subclass may inherit.
     *
     * @param simpleName             the name of the member type
     * @param visibleInOtherPackages whether it is public or protected, or declared in an interface, so a subclass in another package inherits it, too
     */
    record MemberType(String simpleName, boolean visibleInOtherPackages) {
    }

    private record Declaration(int start, int bodyStart, int bodyEnd, boolean annotationType, boolean interfaceType, String simpleName, @Nullable String superclass,
            List<String> supertypes) {
    }

    private AresTestSources() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param path   the path of the file relative to the repository root
     * @param source the content of the file
     * @return how the file uses Ares
     */
    static Summary analyze(String path, String source) {
        String masked = AresPolicyAnnotations.mask(source);
        int[] depth = braceDepths(masked);
        String packageName = packageName(masked);
        List<Declaration> declarations = topLevelDeclarations(masked, depth);
        List<SecurityFrameworkState.UnsupportedReasonType> problems = new ArrayList<>();
        List<AresPolicyAnnotations.Annotation> annotations = AresPolicyAnnotations.find(source);

        List<Map<String, AresPolicyAnnotations.Annotation>> methodPoliciesByType = new ArrayList<>();
        declarations.forEach(_ -> methodPoliciesByType.add(new LinkedHashMap<>()));
        Set<Integer> testDeclarations = AresTestCases.uniqueTestDeclarationOffsets(source);
        List<List<AresPolicyAnnotations.Annotation>> policiesByType = policiesByType(annotations, masked, declarations, depth, testDeclarations, methodPoliciesByType, problems);
        boolean[] registers = new boolean[declarations.size()];
        boolean[] tests = new boolean[declarations.size()];
        markAresClassMarkers(masked, declarations, depth, registers, problems);
        markAresMethodMarkers(masked, declarations, depth, registers, tests, problems);
        if (!aresMarkerOffsets(masked, UNSUPPORTED_ARES_MARKER).isEmpty() || registersSecurityExtensionDirectly(masked)) {
            // Ares also registers its sandbox through these, but the Security Framework only manages tests that use @Public, @Hidden, @PublicTest or @HiddenTest.
            addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES);
        }
        markJUnitTests(masked, declarations, depth, tests, problems);

        List<TypeDeclaration> types = typeDeclarations(masked, packageName, declarations, policiesByType, methodPoliciesByType, registers, tests);
        if (types.stream().filter(TypeDeclaration::isRelevant).count() > 1) {
            addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.MULTIPLE_TOP_LEVEL_TYPES);
        }
        for (int index = 0; index < declarations.size(); index++) {
            if (declarations.get(index).interfaceType() && types.get(index).isRelevant()) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TEST_INTERFACE);
            }
        }
        return new Summary(path, List.copyOf(types), List.copyOf(problems), masked.contains(ARES_1_PACKAGE), aresApis(masked));
    }

    /**
     * Assigns every {@code @Policy} annotation to the top-level type it annotates, or to the method of such a type, and records the annotations that annotate
     * neither. A test method may carry one annotation; Ares applies it to the test case instead of the annotation of the class. Ares reads it only from the
     * test method itself, so an annotation on another method, e.g. a helper the test calls, is not supported.
     *
     * @param testDeclarations     the offsets of the names of the test method declarations of the source whose name no other test method has
     * @param methodPoliciesByType receives the annotations of the methods of each declaration, by its index
     * @return the annotations of each declaration, by its index
     */
    private static List<List<AresPolicyAnnotations.Annotation>> policiesByType(List<AresPolicyAnnotations.Annotation> annotations, String masked, List<Declaration> declarations,
            int[] depth, Set<Integer> testDeclarations, List<Map<String, AresPolicyAnnotations.Annotation>> methodPoliciesByType,
            List<SecurityFrameworkState.UnsupportedReasonType> problems) {
        List<List<AresPolicyAnnotations.Annotation>> policiesByType = new ArrayList<>();
        declarations.forEach(_ -> policiesByType.add(new ArrayList<>()));
        for (AresPolicyAnnotations.Annotation annotation : annotations) {
            if (!annotation.isEvaluable()) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS);
            }
            int owner = depth[annotation.start()] == 0 ? followingDeclaration(declarations, annotation.start()) : -1;
            if (owner < 0) {
                int enclosing = enclosingDeclaration(declarations, annotation.start(), depth);
                String method = enclosing >= 0 && !declarations.get(enclosing).annotationType() ? AresTestCases.methodNameAfter(masked, annotation.end()) : null;
                // The annotation must be on a test declaration itself whose name no other test method has: a method that is no test may share the name
                // of one, e.g. an overload, and an entry could not tell overloaded test methods apart.
                boolean onTest = method != null && testDeclarations.contains(AresTestCases.methodNameOffsetAfter(masked, annotation.end()));
                if (!onTest || methodPoliciesByType.get(enclosing).putIfAbsent(method, annotation) != null) {
                    // A field, a nested type, a method that is no test or one of several test methods with the same name: the editor cannot tell which test
                    // case the annotation belongs to.
                    addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE);
                }
            }
            else if (declarations.get(owner).annotationType()) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.POLICY_ON_ANNOTATION_TYPE);
            }
            else {
                policiesByType.get(owner).add(annotation);
            }
        }
        return policiesByType;
    }

    /**
     * Marks the declarations that register the sandbox through an Ares class marker.
     */
    private static void markAresClassMarkers(String masked, List<Declaration> declarations, int[] depth, boolean[] registers,
            List<SecurityFrameworkState.UnsupportedReasonType> problems) {
        for (String marker : ARES_CLASS_MARKERS) {
            for (int offset : aresMarkerOffsets(masked, marker)) {
                int owner = depth[offset] == 0 ? followingDeclaration(declarations, offset) : -1;
                if (owner >= 0) {
                    registers[owner] = true;
                }
                else {
                    addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TESTS_IN_NESTED_TYPE);
                }
            }
        }
    }

    /**
     * Marks the declarations that register the sandbox through an Ares method marker, whose methods are therefore Ares tests.
     */
    private static void markAresMethodMarkers(String masked, List<Declaration> declarations, int[] depth, boolean[] registers, boolean[] tests,
            List<SecurityFrameworkState.UnsupportedReasonType> problems) {
        for (String marker : ARES_METHOD_MARKERS) {
            for (int offset : aresMarkerOffsets(masked, marker)) {
                int owner = enclosingDeclaration(declarations, offset, depth);
                if (owner >= 0) {
                    registers[owner] = true;
                    tests[owner] = true;
                }
                else {
                    addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TESTS_IN_NESTED_TYPE);
                }
            }
        }
    }

    /**
     * Marks the declarations whose methods are JUnit tests.
     */
    private static void markJUnitTests(String masked, List<Declaration> declarations, int[] depth, boolean[] tests, List<SecurityFrameworkState.UnsupportedReasonType> problems) {
        JUNIT_TEST_MARKER_USES.forEach((simpleName, uses) -> {
            String qualifiedName = JUNIT_TEST_MARKERS.get(simpleName) + "." + simpleName;
            Matcher junitMarkers = uses.matcher(masked);
            while (junitMarkers.find()) {
                // A simple name that an import or a declaration of the file gives to another annotation does not make a test.
                if (junitMarkers.group(1) == null && !simpleNameMayReferTo(masked, simpleName, qualifiedName, junitMarkers.start())) {
                    continue;
                }
                int owner = enclosingDeclaration(declarations, junitMarkers.start(), depth);
                if (owner >= 0) {
                    tests[owner] = true;
                }
                else if (depth[junitMarkers.start()] > 0) {
                    addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TESTS_IN_NESTED_TYPE);
                }
            }
        });
    }

    /**
     * Java resolves a simple type name first to a type the file declares, then to a single-type import, and only then to a type of the package or of a
     * wildcard import (JLS 6.4.1, 7.5). Which types the package and the wildcard imports provide is not known here, so only the first two can rule a name out.
     *
     * @return whether the simple name used at the offset may refer to the type with the qualified name
     */
    private static boolean simpleNameMayReferTo(String masked, String simpleName, String qualifiedName, int offset) {
        Matcher imports = SINGLE_TYPE_IMPORT.matcher(masked);
        while (imports.find()) {
            String imported = imports.group(1);
            if (!imported.equals(qualifiedName) && imported.endsWith("." + simpleName)) {
                return false;
            }
        }
        return !AresPolicyAnnotations.isShadowedAt(masked, simpleName, offset);
    }

    /**
     * @return the top-level types of the file, in the order of their declarations
     */
    private static List<TypeDeclaration> typeDeclarations(String masked, @Nullable String packageName, List<Declaration> declarations,
            List<List<AresPolicyAnnotations.Annotation>> policiesByType, List<Map<String, AresPolicyAnnotations.Annotation>> methodPoliciesByType, boolean[] registers,
            boolean[] tests) {
        List<TypeDeclaration> types = new ArrayList<>();
        Map<Integer, AresTestCases.TestSignature> signatures = new TreeMap<>(AresTestCases.testSignatures(masked));
        // Only the methods the type declares itself can override an inherited test, not those of a nested type, e.g. a jqwik group.
        int[] depth = braceDepths(masked);
        for (int index = 0; index < declarations.size(); index++) {
            Declaration declaration = declarations.get(index);
            String qualifiedName = qualify(packageName, declaration.simpleName());
            List<String> superclass = declaration.superclass() == null ? List.of() : resolveTypeName(masked, packageName, declaration.superclass());
            List<AresTestCases.TestSignature> testSignatures = new ArrayList<>();
            signatures.forEach((offset, signature) -> {
                if (declaration.bodyStart() < offset && offset < declaration.bodyEnd() && depth[offset] == 1) {
                    testSignatures.add(signature);
                }
            });
            // A type declared directly in the body is a member type; a local type of a method or a type nested in a member type is none.
            Set<MemberType> memberTypes = new HashSet<>();
            Matcher memberType = AresPolicyAnnotations.TYPE_DECLARATION.matcher(masked);
            while (memberType.find()) {
                if (declaration.bodyStart() < memberType.start() && memberType.start() < declaration.bodyEnd() && depth[memberType.start()] == 1) {
                    String modifiers = masked.substring(AresTestCases.memberStart(masked, memberType.start()), memberType.start());
                    if (!PRIVATE_MODIFIER.matcher(modifiers).find()) {
                        boolean visible = declaration.interfaceType() || declaration.annotationType() || AresTestCases.VISIBLE_IN_OTHER_PACKAGES.matcher(modifiers).find();
                        memberTypes.add(new MemberType(memberType.group(2), visible));
                    }
                }
            }
            List<List<String>> supertypes = declaration.supertypes().stream().map(name -> resolveTypeName(masked, packageName, name)).toList();
            types.add(new TypeDeclaration(declaration.simpleName(), qualifiedName, superclass, supertypes, List.copyOf(policiesByType.get(index)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(methodPoliciesByType.get(index))), registers[index], tests[index], List.copyOf(testSignatures),
                    Set.copyOf(memberTypes)));
        }
        return types;
    }

    /**
     * The member types that are in scope in the body of a type and shadow a single-type import of the same simple name there: its own and those it inherits
     * from its superclasses and superinterfaces, i.e. the ones that are not private and, from another package, are public or protected. Like Java, the first
     * candidate name of a supertype that exists wins.
     *
     * @param type  a top-level type of the repository
     * @param types the top-level types of the repository, by their qualified names
     * @return the simple names of the member types; empty if the type extends or implements a type outside the repository, e.g. of a library, whose member
     *         types are unknown
     */
    static Optional<Set<String>> memberTypesInScope(TypeDeclaration type, Map<String, TypeDeclaration> types) {
        Set<String> memberTypes = new HashSet<>();
        Set<String> visited = new HashSet<>();
        Deque<TypeDeclaration> pending = new ArrayDeque<>(List.of(type));
        while (!pending.isEmpty()) {
            TypeDeclaration current = pending.pop();
            if (visited.add(current.qualifiedName())) {
                boolean samePackage = current.packageName().equals(type.packageName());
                for (MemberType memberType : current.memberTypes()) {
                    if (samePackage || memberType.visibleInOtherPackages()) {
                        memberTypes.add(memberType.simpleName());
                    }
                }
                for (List<String> candidates : current.supertypes()) {
                    Optional<String> supertype = candidates.stream().filter(types::containsKey).findFirst();
                    if (supertype.isEmpty()) {
                        return Optional.empty();
                    }
                    pending.push(types.get(supertype.get()));
                }
            }
        }
        return Optional.of(memberTypes);
    }

    /**
     * Finds the parts of Ares 2 a file uses besides the sandbox, e.g. the structural test providers, {@code @StrictTimeout} or {@code ReflectionTestUtils}.
     * Removing the Ares library from the build would break such a file, so it shows what has to be migrated first (decision B4 of Ares2 issue #386). The
     * search runs on the masked source and therefore ignores comments and string literals.
     *
     * @param masked the masked source of a file, see {@link AresPolicyAnnotations#mask(String)}
     * @return the types and wildcard-imported packages, relative to the Ares package (e.g. {@code StrictTimeout} or {@code structural.*}), sorted and
     *         each once
     */
    static List<String> aresApis(String masked) {
        Set<String> apis = new TreeSet<>();
        Matcher references = ARES_2_REFERENCE.matcher(masked);
        while (references.find()) {
            String api = WHITESPACE.matcher(references.group(1)).replaceAll("").substring(1);
            if (!SANDBOX_APIS.contains(api)) {
                apis.add(api);
            }
        }
        return List.copyOf(apis);
    }

    /**
     * @return for every offset the number of braces that are open there; the brace that opens a body has the depth outside of it
     */
    private static int[] braceDepths(String masked) {
        int[] depths = new int[masked.length() + 1];
        int depth = 0;
        for (int index = 0; index < masked.length(); index++) {
            char character = masked.charAt(index);
            if (character == '}') {
                depth = Math.max(0, depth - 1);
            }
            depths[index] = depth;
            if (character == '{') {
                depth++;
            }
        }
        depths[masked.length()] = depth;
        return depths;
    }

    private static @Nullable String packageName(String masked) {
        Matcher matcher = PACKAGE_DECLARATION.matcher(masked);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<Declaration> topLevelDeclarations(String masked, int[] depth) {
        List<Declaration> declarations = new ArrayList<>();
        Matcher matcher = TYPE_DECLARATION.matcher(masked);
        while (matcher.find()) {
            int bodyStart = depth[matcher.start()] == 0 ? masked.indexOf('{', matcher.end()) : -1;
            if (bodyStart >= 0) {
                declarations.add(declaration(masked, matcher, bodyStart));
            }
        }
        return declarations;
    }

    /**
     * @param matcher   a match of {@link #TYPE_DECLARATION}
     * @param bodyStart the offset of the brace that opens the body of the matched type
     */
    private static Declaration declaration(String masked, Matcher matcher, int bodyStart) {
        int bodyEnd = AresPolicyAnnotations.matchingBrace(masked, bodyStart);
        String header = masked.substring(matcher.end(), bodyStart);
        Matcher superclass = SUPERCLASS.matcher(header);
        String superclassName = superclass.find() ? superclass.group(1) : null;
        return new Declaration(matcher.start(), bodyStart, bodyEnd < 0 ? masked.length() : bodyEnd, matcher.group(1).startsWith("@"), "interface".equals(matcher.group(1)),
                matcher.group(2), superclassName, supertypeNames(header));
    }

    /**
     * @param header the part of a type declaration between its name and its body
     * @return the names of the types it extends or implements as written, without their type arguments; the record components, the annotations and the
     *         permitted subclasses are none
     */
    private static List<String> supertypeNames(String header) {
        StringBuilder outsideBrackets = new StringBuilder();
        int nesting = 0;
        for (char character : header.toCharArray()) {
            if (character == '<' || character == '(') {
                nesting++;
            }
            else if (character == '>' || character == ')') {
                nesting = Math.max(0, nesting - 1);
            }
            else if (nesting == 0) {
                outsideBrackets.append(character);
            }
        }
        List<String> names = new ArrayList<>();
        boolean supertype = false;
        for (String token : SUPERTYPE_SEPARATOR.split(outsideBrackets)) {
            if ("permits".equals(token)) {
                break;
            }
            if ("extends".equals(token) || "implements".equals(token)) {
                supertype = true;
            }
            else if (supertype && !token.isEmpty() && !token.startsWith("@")) {
                names.add(token);
            }
        }
        return names;
    }

    private static String qualify(@Nullable String packageName, String simpleName) {
        return packageName == null ? simpleName : packageName + "." + simpleName;
    }

    /**
     * Resolves a type name as written in a source to the fully qualified names it may have: a qualified name stays as it is, a simple name refers to a
     * single-type import of that name, or otherwise to a type of the file's package or of one of its wildcard imports. Which of these exists is only known
     * across all sources, so all candidates are returned.
     */
    private static List<String> resolveTypeName(String masked, @Nullable String packageName, String name) {
        if (name.contains(".")) {
            return List.of(name);
        }
        Matcher imports = SINGLE_TYPE_IMPORT.matcher(masked);
        while (imports.find()) {
            if (imports.group(1).endsWith("." + name)) {
                return List.of(imports.group(1));
            }
        }
        List<String> candidates = new ArrayList<>();
        candidates.add(qualify(packageName, name));
        Matcher wildcards = WILDCARD_IMPORT.matcher(masked);
        while (wildcards.find()) {
            candidates.add(wildcards.group(1) + "." + name);
        }
        return List.copyOf(candidates);
    }

    /**
     * @return whether the file registers the security extension itself, e.g. with {@code @ExtendWith(JupiterSecurityExtension.class)}; a mere import does
     *         not count
     */
    private static boolean registersSecurityExtensionDirectly(String masked) {
        boolean simpleNameIsAres = simpleNameRefersTo(masked, SECURITY_EXTENSION, ARES_JUPITER_PACKAGE + "." + SECURITY_EXTENSION);
        Matcher uses = SECURITY_EXTENSION_USE.matcher(masked);
        while (uses.find()) {
            // Only an argument of @ExtendWith registers the extension; the class literal elsewhere, e.g. in a field, does not.
            if (!isInExtendWith(masked, uses.start())) {
                continue;
            }
            boolean partOfLongerName = uses.start() > 0 && (Character.isJavaIdentifierPart(masked.charAt(uses.start() - 1)) || masked.charAt(uses.start() - 1) == '.');
            if (uses.group(1) != null || (simpleNameIsAres && !partOfLongerName && !AresPolicyAnnotations.isShadowedAt(masked, SECURITY_EXTENSION, uses.start()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param packageName the package of the annotation
     * @param simpleName  the simple name of the annotation
     * @return the regular expression of the uses of the annotation, with or without its package, which is the first group
     */
    private static String annotationUse(String packageName, String simpleName) {
        return AresPolicyAnnotations.ANNOTATION_START + "(" + AresPolicyAnnotations.packagePrefix(packageName) + ")?" + simpleName + END_OF_IDENTIFIER;
    }

    /**
     * @return whether the offset lies in the argument list of JUnit's {@code @ExtendWith}; an annotation of the same simple name that an import or a declaration
     *         of the file gives to another type does not count
     */
    private static boolean isInExtendWith(String masked, int offset) {
        Matcher extendWith = EXTEND_WITH.matcher(masked);
        while (extendWith.find() && extendWith.end() <= offset) {
            if (extendWith.group(1) == null && !simpleNameMayReferTo(masked, EXTEND_WITH_NAME, JUNIT_EXTENSION_PACKAGE + "." + EXTEND_WITH_NAME, extendWith.start())) {
                continue;
            }
            int open = extendWith.end();
            while (open < masked.length() && Character.isWhitespace(masked.charAt(open))) {
                open++;
            }
            if (open < masked.length() && masked.charAt(open) == '(' && open < offset) {
                int close = AresPolicyAnnotations.matchingParenthesis(masked, open);
                if (close < 0 || offset < close) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return the index of the first declaration that starts after the offset and has no other declaration's body in between, i.e. the one an annotation
     *         at the offset belongs to; -1 if there is none
     */
    private static int followingDeclaration(List<Declaration> declarations, int offset) {
        for (int index = 0; index < declarations.size(); index++) {
            Declaration declaration = declarations.get(index);
            if (declaration.start() > offset) {
                return index;
            }
            if (declaration.bodyEnd() >= offset) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * @return the index of the top-level declaration whose body directly contains the offset, i.e. a member of that type and not of a nested type; -1 if
     *         there is none
     */
    private static int enclosingDeclaration(List<Declaration> declarations, int offset, int[] depth) {
        if (depth[offset] != 1) {
            return -1;
        }
        for (int index = 0; index < declarations.size(); index++) {
            Declaration declaration = declarations.get(index);
            if (declaration.bodyStart() < offset && offset < declaration.bodyEnd()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Finds the uses of an Ares 2 Jupiter annotation. The fully qualified name always refers to Ares; the simple name does if the file imports it, directly
     * or with a wildcard import of its package while no other type of that name is imported.
     *
     * @return the offsets of the {@code @} of every use
     */
    private static List<Integer> aresMarkerOffsets(String masked, String simpleName) {
        String qualifiedName = ARES_JUPITER_PACKAGE + "." + simpleName;
        boolean simpleNameIsAres = simpleNameRefersTo(masked, simpleName, qualifiedName);
        Matcher uses = MARKER_USES.get(simpleName).matcher(masked);
        List<Integer> offsets = new ArrayList<>();
        while (uses.find()) {
            boolean qualified = uses.group(1) != null;
            boolean partOfLongerName = uses.start() > 0 && (Character.isJavaIdentifierPart(masked.charAt(uses.start() - 1)) || masked.charAt(uses.start() - 1) == '.');
            // A type of the same name that the file declares itself shadows the Ares one where it is in scope.
            if (qualified || (simpleNameIsAres && !partOfLongerName && !AresPolicyAnnotations.isShadowedAt(masked, simpleName, uses.start()))) {
                offsets.add(uses.start());
            }
        }
        return offsets;
    }

    private static boolean simpleNameRefersTo(String masked, String simpleName, String qualifiedName) {
        boolean importsIt = false;
        boolean importsOther = false;
        Matcher imports = SINGLE_TYPE_IMPORT.matcher(masked);
        while (imports.find()) {
            String imported = imports.group(1);
            if (qualifiedName.equals(imported)) {
                importsIt = true;
            }
            else if (imported.endsWith("." + simpleName)) {
                importsOther = true;
            }
        }
        return importsIt || (!importsOther && ARES_JUPITER_WILDCARD_IMPORT.matcher(masked).find());
    }

    private static void addOnce(List<SecurityFrameworkState.UnsupportedReasonType> problems, SecurityFrameworkState.UnsupportedReasonType problem) {
        if (!problems.contains(problem)) {
            problems.add(problem);
        }
    }

    /**
     * @return the top-level type of the summary that takes part in the sandbox decision, if there is exactly one
     */
    static Optional<TypeDeclaration> relevantType(Summary summary) {
        List<TypeDeclaration> relevant = summary.topLevelTypes().stream().filter(TypeDeclaration::isRelevant).toList();
        return relevant.size() == 1 ? Optional.of(relevant.getFirst()) : Optional.empty();
    }
}
