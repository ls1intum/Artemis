package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /** A type declaration; the keyword must not follow a dot, so {@code Foo.class} is no declaration. */
    private static final Pattern TYPE_DECLARATION = Pattern.compile("(@\\s*interface|(?<![\\w.$])(?:class|interface|enum|record))\\s+([A-Za-z_$][\\w$]*)");

    private static final Pattern SUPERCLASS = Pattern.compile("^\\s*(?:<[^{]*?>)?\\s*extends\\s+([\\w.$]+)");

    /** A JUnit annotation that makes a method a test, with or without its package. */
    private static final Pattern JUNIT_TEST_MARKER = Pattern
            .compile("@(?:org\\.junit\\.jupiter\\.api\\.)?(?:Test|RepeatedTest|TestFactory|TestTemplate)\\b|@(?:org\\.junit\\.jupiter\\.params\\.)?ParameterizedTest\\b");

    private static final Pattern SINGLE_TYPE_IMPORT = Pattern.compile("\\bimport\\s+([\\w.]+)\\s*;");

    private static final List<String> ARES_CLASS_MARKERS = List.of("Public", "Hidden");

    private static final List<String> ARES_METHOD_MARKERS = List.of("PublicTest", "HiddenTest");

    /** An Ares annotation that registers the sandbox, but that the Security Framework does not manage. */
    private static final String UNSUPPORTED_ARES_MARKER = "JupiterAresTest";

    private static final String SECURITY_EXTENSION = "JupiterSecurityExtension";

    /** The security extension registered directly, e.g. in {@code @ExtendWith(JupiterSecurityExtension.class)}, with or without its package. */
    private static final Pattern SECURITY_EXTENSION_USE = Pattern.compile("(" + Pattern.quote(ARES_JUPITER_PACKAGE + ".") + ")?" + SECURITY_EXTENSION + "\\s*\\.\\s*class\\b");

    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private static final Pattern WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+([\\w.]+)\\.\\*\\s*;");

    private static final Pattern ARES_JUPITER_WILDCARD_IMPORT = Pattern.compile("\\bimport\\s+" + Pattern.quote(ARES_JUPITER_PACKAGE) + "\\.\\*\\s*;");

    /**
     * A reference to an Ares 2 type or package in an import, an annotation or the code: the package, any lower-case subpackages and then a type or the
     * {@code *} of a wildcard import. A static import ends at the type, so its member is not part of the match.
     */
    private static final Pattern ARES_2_REFERENCE = Pattern
            .compile("(?<![\\w$.])" + Pattern.quote(ARES_2_PACKAGE) + "((?:\\s*\\.\\s*[a-z_][\\w$]*)*\\s*\\.\\s*(?:[A-Z][\\w$]*|\\*))");

    /** The JUnit 5 subpackage of Ares, relative to {@link #ARES_2_PACKAGE}, as a prefix of the API names in {@link #SANDBOX_APIS}. */
    private static final String JUPITER = "jupiter.";

    /**
     * The parts of Ares the Security Framework itself switches on and off: the policy annotation and the markers and extension that register the sandbox,
     * relative to {@link #ARES_2_PACKAGE}. Tests that use only these keep working without the Ares library once the sandbox is off.
     */
    private static final Set<String> SANDBOX_APIS = Stream.concat(Stream.of("Policy", JUPITER + "*", JUPITER + SECURITY_EXTENSION, JUPITER + UNSUPPORTED_ARES_MARKER),
            Stream.concat(ARES_CLASS_MARKERS.stream(), ARES_METHOD_MARKERS.stream()).map(marker -> JUPITER + marker)).collect(Collectors.toUnmodifiableSet());

    /** The uses of each Ares marker, with or without its package, compiled once. */
    private static final Map<String, Pattern> MARKER_USES = Stream.of(ARES_CLASS_MARKERS.stream(), ARES_METHOD_MARKERS.stream(), Stream.of(UNSUPPORTED_ARES_MARKER))
            .flatMap(Function.identity())
            .collect(Collectors.toUnmodifiableMap(Function.identity(), marker -> Pattern.compile("@(" + Pattern.quote(ARES_JUPITER_PACKAGE + ".") + ")?" + marker + "\\b")));

    /**
     * A top-level type of a source file.
     *
     * @param simpleName       the name as declared
     * @param qualifiedName    the name including the package of the file
     * @param superclass       the fully qualified names the class it extends may have: the one its single-type import or qualified name gives, or else the
     *                             one in its own package and one per wildcard import; empty if it extends nothing
     * @param policies         its own Ares {@code @Policy} annotations
     * @param registersSandbox whether it registers the Ares sandbox through {@code @Public} or {@code @Hidden} on the type or {@code @PublicTest} or
     *                             {@code @HiddenTest} on a method
     * @param containsTests    whether one of its methods is a JUnit or Ares test
     */
    record TypeDeclaration(String simpleName, String qualifiedName, List<String> superclass, List<AresPolicyAnnotations.Annotation> policies, boolean registersSandbox,
            boolean containsTests) {

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

    private record Declaration(int start, int bodyStart, int bodyEnd, boolean annotationType, String simpleName, @Nullable String superclass) {
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

        List<List<AresPolicyAnnotations.Annotation>> policiesByType = policiesByType(annotations, declarations, depth, problems);
        boolean[] registers = new boolean[declarations.size()];
        boolean[] tests = new boolean[declarations.size()];
        markAresMarkers(masked, declarations, depth, registers, tests, problems);
        if (!aresMarkerOffsets(masked, UNSUPPORTED_ARES_MARKER).isEmpty() || registersSecurityExtensionDirectly(masked)) {
            // Ares also registers its sandbox through these, but the Security Framework only manages tests that use @Public, @Hidden, @PublicTest or @HiddenTest.
            addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES);
        }
        markJUnitTests(masked, declarations, depth, tests, problems);

        List<TypeDeclaration> types = typeDeclarations(masked, packageName, declarations, policiesByType, registers, tests);
        if (types.stream().filter(TypeDeclaration::isRelevant).count() > 1) {
            addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.MULTIPLE_TOP_LEVEL_TYPES);
        }
        return new Summary(path, List.copyOf(types), List.copyOf(problems), masked.contains(ARES_1_PACKAGE), aresApis(masked));
    }

    /**
     * Assigns every {@code @Policy} annotation to the top-level type it annotates and records the annotations that annotate no such type.
     *
     * @return the annotations of each declaration, by its index
     */
    private static List<List<AresPolicyAnnotations.Annotation>> policiesByType(List<AresPolicyAnnotations.Annotation> annotations, List<Declaration> declarations, int[] depth,
            List<SecurityFrameworkState.UnsupportedReasonType> problems) {
        List<List<AresPolicyAnnotations.Annotation>> policiesByType = new ArrayList<>();
        declarations.forEach(_ -> policiesByType.add(new ArrayList<>()));
        for (AresPolicyAnnotations.Annotation annotation : annotations) {
            if (!annotation.isEvaluable()) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS);
            }
            int owner = depth[annotation.start()] == 0 ? followingDeclaration(declarations, annotation.start()) : -1;
            if (owner < 0) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE);
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
     * Marks the declarations that register the sandbox through an Ares class or method marker, and those whose methods are Ares tests.
     */
    private static void markAresMarkers(String masked, List<Declaration> declarations, int[] depth, boolean[] registers, boolean[] tests,
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
        Matcher junitMarkers = JUNIT_TEST_MARKER.matcher(masked);
        while (junitMarkers.find()) {
            int owner = enclosingDeclaration(declarations, junitMarkers.start(), depth);
            if (owner >= 0) {
                tests[owner] = true;
            }
            else if (depth[junitMarkers.start()] > 0) {
                addOnce(problems, SecurityFrameworkState.UnsupportedReasonType.TESTS_IN_NESTED_TYPE);
            }
        }
    }

    /**
     * @return the top-level types of the file, in the order of their declarations
     */
    private static List<TypeDeclaration> typeDeclarations(String masked, @Nullable String packageName, List<Declaration> declarations,
            List<List<AresPolicyAnnotations.Annotation>> policiesByType, boolean[] registers, boolean[] tests) {
        List<TypeDeclaration> types = new ArrayList<>();
        for (int index = 0; index < declarations.size(); index++) {
            Declaration declaration = declarations.get(index);
            String qualifiedName = qualify(packageName, declaration.simpleName());
            List<String> superclass = declaration.superclass() == null ? List.of() : resolveTypeName(masked, packageName, declaration.superclass());
            types.add(new TypeDeclaration(declaration.simpleName(), qualifiedName, superclass, List.copyOf(policiesByType.get(index)), registers[index], tests[index]));
        }
        return types;
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
        int bodyEnd = closingBrace(masked, bodyStart);
        String header = masked.substring(matcher.end(), bodyStart);
        Matcher superclass = SUPERCLASS.matcher(header);
        String superclassName = superclass.find() ? superclass.group(1) : null;
        return new Declaration(matcher.start(), bodyStart, bodyEnd < 0 ? masked.length() : bodyEnd, matcher.group(1).startsWith("@"), matcher.group(2), superclassName);
    }

    private static int closingBrace(String masked, int open) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char character = masked.charAt(index);
            if (character == '{') {
                depth++;
            }
            else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
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
            boolean partOfLongerName = uses.start() > 0 && (Character.isJavaIdentifierPart(masked.charAt(uses.start() - 1)) || masked.charAt(uses.start() - 1) == '.');
            if (uses.group(1) != null || (simpleNameIsAres && !partOfLongerName)) {
                return true;
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
            if (qualified || (simpleNameIsAres && !partOfLongerName)) {
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
