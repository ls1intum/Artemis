package de.tum.cit.aet.artemis.programming.service.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Reads and rewrites the Ares 2 security setup inside a checked-out exercise-tests repository.
 * <p>
 * The Java templates ship Ares 2 already enabled: the test classes carry {@code @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")}, the
 * repository root contains {@code SecurityPolicy.yaml}, and the build file pins the Ares version ({@code aresVersion} in Gradle, {@code ares.version} in
 * Maven). This class is the single place that knows that layout:
 * <ul>
 * <li>the state of the Security Framework is what Ares applies when the tests run, see {@link #evaluate(Map, PolicyFileReader, BuildFiles)},</li>
 * <li>deactivating deletes the policy file and switches every {@code @Policy} annotation to {@code activated = false}, so Ares stops enforcing a sandbox
 * (removing the annotation alone would not do that, because Ares then applies its strict default policy),</li>
 * <li>activating writes a policy file and switches the annotations back on.</li>
 * </ul>
 * The Ares library itself stays on the test classpath, because the template tests also use its non-security APIs (structural tests, reflection helpers,
 * timeouts). All methods are side-effect free apart from the files they are asked to write.
 */
public final class SecurityPolicyRepositoryFiles {

    /** Name of the policy file at the root of the exercise-tests repository, as referenced by the template test classes. */
    public static final String POLICY_FILE_NAME = "SecurityPolicy.yaml";

    private static final String RESOURCE_PERMISSIONS_LINE = "  theFollowingResourceAccessesArePermitted:";

    /** The section of a generated policy that permits no resource access at all. */
    public static final String NO_RESOURCE_PERMISSIONS = RESOURCE_PERMISSIONS_LINE + """

                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations: [ ]
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    private static final String GRADLE_BUILD_FILE = "build.gradle";

    private static final String MAVEN_BUILD_FILE = "pom.xml";

    private static final Pattern GRADLE_ARES_VERSION = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(aresVersion\\s*=\\s*['\"])([^'\"]+)(['\"])");

    private static final Pattern MAVEN_ARES_VERSION = Pattern.compile("(<ares\\.version>)([^<]+)(</ares\\.version>)");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:");

    private static final Pattern BLANK_OR_COMMENT_LINE = Pattern.compile("^[ \\t]*(#.*)?$");

    /** Directories that never contain test sources of the exercise, wherever they appear. */
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", "node_modules");

    /**
     * Build output directories. They are only skipped next to a build file, i.e. at the root of a (sub)project, so a source package that happens to be
     * called {@code build} or {@code target} is still scanned.
     */
    private static final Set<String> BUILD_OUTPUT_DIRECTORIES = Set.of("build", "target");

    private static final Set<String> BUILD_FILES = Set.of(GRADLE_BUILD_FILE, "build.gradle.kts", MAVEN_BUILD_FILE);

    /** The build tool of the exercise-tests repository, which selects the Ares language configuration of a generated policy. */
    public enum BuildTool {

        GRADLE("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ"), MAVEN("JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ");

        private final String aresLanguageConfiguration;

        BuildTool(String aresLanguageConfiguration) {
            this.aresLanguageConfiguration = aresLanguageConfiguration;
        }

        /**
         * @return the language configuration of a policy Artemis generates for this build tool
         */
        public String aresLanguageConfiguration() {
            return aresLanguageConfiguration;
        }
    }

    private SecurityPolicyRepositoryFiles() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The build files at the root of the tests repository, as far as they exist.
     *
     * @param gradleBuild content of {@code build.gradle}
     * @param mavenPom    content of {@code pom.xml}
     */
    public record BuildFiles(@Nullable String gradleBuild, @Nullable String mavenPom) {

        /**
         * @return whether a build file refers to the Ares 1 library {@code artemis-java-test-sandbox}
         */
        boolean usesAres1() {
            return (gradleBuild != null && gradleBuild.contains(ARES_1_ARTIFACT)) || (mavenPom != null && mavenPom.contains(ARES_1_ARTIFACT));
        }
    }

    /**
     * The content of a policy file as far as it could be read.
     *
     * @param problem why the file cannot be used; absent if it was read
     * @param content the content; absent if there is a problem
     */
    public record PolicyFileContent(SecurityFrameworkState.@Nullable PolicyFileProblem problem, @Nullable String content) {

        static PolicyFileContent of(String content) {
            return new PolicyFileContent(null, content);
        }

        static PolicyFileContent failed(SecurityFrameworkState.PolicyFileProblem problem) {
            return new PolicyFileContent(problem, null);
        }
    }

    /**
     * Reads a policy file of the repository by its validated path, see {@link #validatePolicyPath(String)}. A checkout and a committed tree implement it
     * differently, but both reject everything that is not a regular file and check the size before reading.
     */
    @FunctionalInterface
    public interface PolicyFileReader {

        /**
         * @param path a validated path relative to the repository root
         * @return the content or the problem
         */
        PolicyFileContent read(String path);
    }

    /**
     * Determines the Security Framework state of a tests repository, as Ares applies it when the tests run. This is the single rule for the state:
     * <ul>
     * <li>{@code UNSUPPORTED} if the repository uses Ares in a way the Security Framework cannot manage reliably, see
     * {@link SecurityFrameworkState.UnsupportedReasonType};</li>
     * <li>{@code MISCONFIGURED} if an active {@code @Policy} annotation points to a policy file that is missing, invalid or unreadable, because Ares then fails
     * the tests. A repository that pins the Ares version {@link SecurityPolicySchema} was verified against has its policy files checked against the rules of
     * that version; for any other version a file only has to be a YAML mapping, so {@code CONFIGURED} does not establish that this Ares version accepts it;</li>
     * <li>{@code CONFIGURED} if at least one test class has an active {@code @Policy} annotation;</li>
     * <li>{@code INACTIVE} otherwise.</li>
     * </ul>
     * Only the policy files of active annotations are read, because Ares does not read the others either.
     *
     * @param javaSources the Java sources of the repository by their path relative to its root, see {@link #isJavaSourcePath(String, Predicate)}
     * @param reader      reads the policy files of the repository
     * @param buildFiles  the build files at the root of the repository
     * @return the state
     */
    public static SecurityFrameworkState evaluate(Map<String, String> javaSources, PolicyFileReader reader, BuildFiles buildFiles) {
        List<AresTestSources.Summary> summaries = javaSources.entrySet().stream().map(source -> AresTestSources.analyze(source.getKey(), source.getValue())).toList();
        List<SecurityFrameworkState.AresApiUse> aresApiUses = summaries.stream()
                .flatMap(summary -> summary.aresApis().stream().map(api -> new SecurityFrameworkState.AresApiUse(summary.path(), api)))
                .sorted(Comparator.comparing(SecurityFrameworkState.AresApiUse::location).thenComparing(SecurityFrameworkState.AresApiUse::api)).toList();
        List<AresTestSources.TypeDeclaration> testClasses = testClasses(summaries);
        List<SecurityFrameworkState.UnsupportedReason> reasons = unsupportedReasons(summaries, testClasses, buildFiles);
        if (!reasons.isEmpty()) {
            return new SecurityFrameworkState(SecurityActivationStatus.UNSUPPORTED, List.of(), List.of(), List.copyOf(reasons), aresApiUses);
        }

        List<String> disabledTestClasses = new ArrayList<>();
        Map<String, List<String>> classesByPolicyPath = new TreeMap<>();
        boolean anyActive = false;
        for (AresTestSources.TypeDeclaration testClass : testClasses) {
            AresPolicyAnnotations.Annotation annotation = testClass.policies().getFirst();
            if (!annotation.isActivated()) {
                disabledTestClasses.add(testClass.qualifiedName());
                continue;
            }
            anyActive = true;
            annotation.policyPath().filter(path -> !path.isBlank())
                    .ifPresent(path -> classesByPolicyPath.computeIfAbsent(path, _ -> new ArrayList<>()).add(testClass.qualifiedName()));
            // Ares applies the annotation of a test method instead of the one of the class, so the method's policy file is read as well.
            testClass.methodPolicies().forEach((method, methodAnnotation) -> {
                if (methodAnnotation.isEvaluable() && methodAnnotation.isActivated()) {
                    methodAnnotation.policyPath().filter(path -> !path.isBlank()).ifPresent(path -> classesByPolicyPath.computeIfAbsent(path, _ -> new ArrayList<>())
                            .add(testClass.qualifiedName() + SecurityTestAssignments.METHOD_SEPARATOR + method));
                }
            });
        }
        boolean verifiedAresVersion = parseAresVersion(buildFiles.gradleBuild(), buildFiles.mavenPom()).filter(SecurityPolicySchema.VERIFIED_ARES_VERSION::equals).isPresent();
        List<SecurityFrameworkState.PolicyFile> policyFiles = new ArrayList<>();
        classesByPolicyPath.forEach((path, referencedBy) -> policyFiles.add(policyFile(path, referencedBy.stream().sorted().toList(), reader, verifiedAresVersion)));

        SecurityActivationStatus status;
        if (policyFiles.stream().anyMatch(policyFile -> policyFile.problem() != null && policyFile.problem().breaksTheSandbox())) {
            status = SecurityActivationStatus.MISCONFIGURED;
        }
        else {
            status = anyActive ? SecurityActivationStatus.CONFIGURED : SecurityActivationStatus.INACTIVE;
        }
        disabledTestClasses.sort(String::compareTo);
        return new SecurityFrameworkState(status, List.copyOf(disabledTestClasses), List.copyOf(policyFiles), List.of(), aresApiUses);
    }

    /**
     * @return why the Security Framework cannot manage the repository, in this order: Ares 1, no pinned Ares version, the problems of each source file, and the
     *         problems of the test classes; empty if it can manage it
     */
    private static List<SecurityFrameworkState.UnsupportedReason> unsupportedReasons(List<AresTestSources.Summary> summaries, List<AresTestSources.TypeDeclaration> testClasses,
            BuildFiles buildFiles) {
        List<SecurityFrameworkState.UnsupportedReason> reasons = new ArrayList<>();
        boolean usesAres1 = buildFiles.usesAres1() || summaries.stream().anyMatch(AresTestSources.Summary::usesAres1);
        if (usesAres1) {
            reasons.add(new SecurityFrameworkState.UnsupportedReason(buildFiles.gradleBuild() != null ? GRADLE_BUILD_FILE : MAVEN_BUILD_FILE,
                    SecurityFrameworkState.UnsupportedReasonType.ARES_1));
        }
        if (detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom()).isEmpty()) {
            reasons.add(new SecurityFrameworkState.UnsupportedReason(buildFiles.mavenPom() != null ? MAVEN_BUILD_FILE : GRADLE_BUILD_FILE,
                    SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        }
        summaries.forEach(summary -> summary.problems().forEach(problem -> reasons.add(new SecurityFrameworkState.UnsupportedReason(summary.path(), problem))));
        if (!usesAres1) {
            reasons.addAll(testClassReasons(testClasses));
        }
        return reasons;
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository; it may be relative or contain {@code .} and {@code ..}, e.g. below a relative
     *                           {@code artemis.temp-path}
     * @return the state of the checkout, see {@link #evaluate(Map, PolicyFileReader, BuildFiles)}
     */
    public static SecurityFrameworkState evaluate(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return evaluate(javaSources(root), path -> readPolicyFile(root, path), buildFiles(root));
    }

    /**
     * The test classes are the relevant top-level types of the sources, see {@link AresTestSources.TypeDeclaration#isRelevant()}, and every class that
     * extends one of them, because it inherits its tests. Such a subclass must then carry its own annotation as well.
     */
    static List<AresTestSources.TypeDeclaration> testClasses(List<AresTestSources.Summary> summaries) {
        List<AresTestSources.TypeDeclaration> allTypes = summaries.stream().flatMap(summary -> summary.topLevelTypes().stream()).toList();
        Set<String> allTypeNames = allTypes.stream().map(AresTestSources.TypeDeclaration::qualifiedName).collect(Collectors.toSet());
        Set<String> testClassNames = new HashSet<>();
        allTypes.stream().filter(AresTestSources.TypeDeclaration::isRelevant).forEach(type -> testClassNames.add(type.qualifiedName()));
        boolean added = true;
        while (added) {
            added = false;
            for (AresTestSources.TypeDeclaration type : allTypes) {
                if (superclassOf(type, allTypeNames).filter(testClassNames::contains).isPresent() && testClassNames.add(type.qualifiedName())) {
                    added = true;
                }
            }
        }
        return allTypes.stream().filter(type -> testClassNames.contains(type.qualifiedName())).sorted(Comparator.comparing(AresTestSources.TypeDeclaration::qualifiedName))
                .toList();
    }

    /**
     * The class a type extends, among the types of the repository: like Java, the first candidate that exists wins, so a class of the type's own package
     * takes precedence over one of a wildcard import.
     */
    private static Optional<String> superclassOf(AresTestSources.TypeDeclaration type, Set<String> allTypeNames) {
        return type.superclass().stream().filter(allTypeNames::contains).findFirst();
    }

    private static List<SecurityFrameworkState.UnsupportedReason> testClassReasons(List<AresTestSources.TypeDeclaration> testClasses) {
        if (!testClasses.isEmpty() && testClasses.stream().noneMatch(testClass -> testClass.registersSandbox() || !testClass.policies().isEmpty())) {
            return List.of(new SecurityFrameworkState.UnsupportedReason(testClasses.getFirst().qualifiedName(), SecurityFrameworkState.UnsupportedReasonType.NO_ARES_2));
        }
        List<SecurityFrameworkState.UnsupportedReason> reasons = new ArrayList<>();
        Map<String, AresTestSources.TypeDeclaration> testClassesByName = new HashMap<>();
        testClasses.forEach(testClass -> testClassesByName.put(testClass.qualifiedName(), testClass));
        for (AresTestSources.TypeDeclaration testClass : testClasses) {
            if (testClass.policies().size() != 1) {
                reasons.add(new SecurityFrameworkState.UnsupportedReason(testClass.qualifiedName(),
                        testClass.policies().isEmpty() ? SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY
                                : SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS));
            }
            if (!testClass.registersSandbox()) {
                reasons.add(new SecurityFrameworkState.UnsupportedReason(testClass.qualifiedName(), SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES));
            }
            if (hasActiveMethodPolicyWhileSwitchedOff(testClass, testClassesByName)) {
                reasons.add(
                        new SecurityFrameworkState.UnsupportedReason(testClass.qualifiedName(), SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS));
            }
        }
        return reasons;
    }

    /**
     * Ares applies the annotation of a test method instead of the one of its class, so an active method annotation in a class whose annotation switches the
     * sandbox off runs that test with the sandbox. That holds for the test methods the class inherits from other test classes as well, because Ares reads the
     * annotation of the method where it is declared before the one of the class that runs it. Neither the state of the class nor its assignments could show
     * that, so such a class is not supported.
     */
    private static boolean hasActiveMethodPolicyWhileSwitchedOff(AresTestSources.TypeDeclaration testClass, Map<String, AresTestSources.TypeDeclaration> testClassesByName) {
        if (testClass.policies().size() != 1 || !testClass.policies().getFirst().isEvaluable() || testClass.policies().getFirst().isActivated()) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        Optional<AresTestSources.TypeDeclaration> declaring = Optional.of(testClass);
        while (declaring.isPresent() && visited.add(declaring.get().qualifiedName())) {
            if (declaring.get().methodPolicies().values().stream().anyMatch(annotation -> annotation.isEvaluable() && annotation.isActivated())) {
                return true;
            }
            declaring = superclassOf(declaring.get(), testClassesByName.keySet()).map(testClassesByName::get);
        }
        return false;
    }

    private static SecurityFrameworkState.PolicyFile policyFile(String path, List<String> referencedBy, PolicyFileReader reader, boolean verifiedAresVersion) {
        Optional<String> validPath = validatePolicyPath(path);
        if (validPath.isEmpty()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.INVALID_PATH, List.of());
        }
        PolicyFileContent content = reader.read(validPath.get());
        if (content.problem() != null || content.content() == null) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, content.problem(), List.of());
        }
        SecurityPolicyYaml.ReadPolicy policy = verifiedAresVersion ? SecurityPolicySchema.read(content.content()) : SecurityPolicyYaml.read(content.content());
        if (!policy.valid()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE, List.of());
        }
        if (policy.testClasses().isEmpty()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND, List.of());
        }
        List<String> missing = referencedBy.stream().map(SecurityTestAssignments::testClassOf).distinct().filter(testClass -> !policy.testClasses().get().contains(testClass))
                .toList();
        return new SecurityFrameworkState.PolicyFile(path, referencedBy, null, missing);
    }

    /**
     * The boundary for every policy path that comes from an annotation: only a relative path that stays inside the repository and does not point into its
     * {@code .git} directory is ever read, restored or written.
     *
     * @param path a policy path as an annotation names it
     * @return the normalized path relative to the repository root with forward slashes; empty if the path is not acceptable
     */
    public static Optional<String> validatePolicyPath(String path) {
        if (path.isBlank() || path.indexOf('\0') >= 0 || path.startsWith("/") || path.startsWith("\\") || WINDOWS_DRIVE.matcher(path).lookingAt()) {
            return Optional.empty();
        }
        String normalized;
        try {
            normalized = Path.of(path).normalize().toString().replace('\\', '/');
        }
        catch (InvalidPathException _) {
            return Optional.empty();
        }
        if (normalized.isEmpty() || normalized.equals("..") || normalized.startsWith("../") || normalized.equals(".git") || normalized.startsWith(".git/")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    /**
     * Reads a policy file of a checkout without following symbolic links, neither for the file nor for a directory above it.
     *
     * @param root an absolute, normalized repository root
     * @param path a validated path relative to the root
     * @return the content or the problem
     */
    static PolicyFileContent readPolicyFile(Path root, String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || hasSymbolicLinkOnPath(root, file)) {
            return PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.MISSING);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
        }
        try {
            if (Files.size(file) > SecurityPolicyYaml.MAX_POLICY_SIZE) {
                return PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.TOO_LARGE);
            }
            return PolicyFileContent.of(Files.readString(file, StandardCharsets.UTF_8));
        }
        catch (IOException _) {
            return PolicyFileContent.failed(SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE);
        }
    }

    private static boolean hasSymbolicLinkOnPath(Path root, Path file) {
        for (Path current = file; current != null && current.startsWith(root) && !current.equals(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param repositoryRoot an absolute, normalized repository root
     * @return the Java sources of the checkout by their path relative to the root, with forward slashes
     */
    public static Map<String, String> javaSources(Path repositoryRoot) {
        Map<String, String> javaSources = new TreeMap<>();
        for (Path javaFile : javaFiles(repositoryRoot)) {
            javaSources.put(repositoryRoot.relativize(javaFile).toString().replace('\\', '/'), readFile(javaFile));
        }
        return javaSources;
    }

    static BuildFiles buildFiles(Path repositoryRoot) {
        return new BuildFiles(readFileIfPresent(repositoryRoot.resolve(GRADLE_BUILD_FILE)).orElse(null), readFileIfPresent(repositoryRoot.resolve(MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * Decides whether an Ares {@code @Policy} annotation enforces its policy.
     */
    @FunctionalInterface
    public interface AnnotationDecision {

        /**
         * @param testClass the fully qualified name of the test class of the Java file; null if the file has none
         * @param method    the test method the annotation is on; null for the annotation of the class
         * @return whether the annotation should enforce its policy
         */
        boolean isActive(@Nullable String testClass, @Nullable String method);
    }

    /**
     * @param testClass the fully qualified name of a test class
     * @param method    the test method of an annotation; null for the annotation of the class
     * @return the key of the annotation in {@link #annotationStates(Map)}
     */
    public static String annotationKey(String testClass, @Nullable String method) {
        return method == null ? testClass : testClass + SecurityTestAssignments.METHOD_SEPARATOR + method;
    }

    /**
     * @param javaSources the Java sources of a repository by their path relative to its root
     * @return for every evaluable {@code @Policy} annotation of a test class or of one of its test methods whether it is switched on, by
     *         {@link #annotationKey(String, String)}
     */
    public static Map<String, Boolean> annotationStates(Map<String, String> javaSources) {
        Map<String, Boolean> states = new TreeMap<>();
        javaSources.forEach((path, source) -> AresTestSources.relevantType(AresTestSources.analyze(path, source))
                .filter(type -> type.policies().size() == 1 && type.policies().getFirst().isEvaluable()).ifPresent(type -> {
                    states.put(annotationKey(type.qualifiedName(), null), type.policies().getFirst().isActivated());
                    type.methodPolicies().forEach((method, annotation) -> {
                        if (annotation.isEvaluable()) {
                            states.put(annotationKey(type.qualifiedName(), method), annotation.isActivated());
                        }
                    });
                }));
        return states;
    }

    /**
     * Decides which files are scanned as Java sources. Both the scan of a checkout and the scan of a committed tree use it, so both see the same sources.
     *
     * @param path   a regular file of the repository, relative to its root, with forward slashes
     * @param isFile whether a path, relative to the root, is a regular file of the repository
     * @return whether the path is a Java file outside the tool and build output directories, i.e. one that {@link #setPolicyAnnotationsActive} would also
     *         rewrite
     */
    public static boolean isJavaSourcePath(String path, Predicate<String> isFile) {
        if (!path.endsWith(".java")) {
            return false;
        }
        String[] segments = path.split("/");
        StringBuilder directory = new StringBuilder();
        // The last segment is the file itself; only the directories above it can hold tool or build output.
        for (int index = 0; index < segments.length - 1; index++) {
            String name = segments[index];
            if (name.isEmpty()) {
                continue;
            }
            if (IGNORED_DIRECTORIES.contains(name) || (BUILD_OUTPUT_DIRECTORIES.contains(name) && containsBuildFile(directory.toString(), isFile))) {
                return false;
            }
            directory.append(name).append('/');
        }
        return true;
    }

    /**
     * @param directory a directory of the repository, relative to its root, empty or ending with a slash
     * @return whether the directory is the root of a (sub)project, i.e. it contains a build file
     */
    private static boolean containsBuildFile(String directory, Predicate<String> isFile) {
        return BUILD_FILES.stream().anyMatch(buildFile -> isFile.test(directory + buildFile));
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the build tool of the repository, if it has a Gradle or Maven build file that pins an Ares version
     */
    public static Optional<BuildTool> detectBuildTool(Path repositoryRoot) {
        return detectBuildTool(readFileIfPresent(repositoryRoot.resolve(GRADLE_BUILD_FILE)).orElse(null), readFileIfPresent(repositoryRoot.resolve(MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * @param gradleBuild content of {@code build.gradle}, if the repository has one
     * @param mavenPom    content of {@code pom.xml}, if the repository has one
     * @return the build tool whose build file pins an Ares version
     */
    static Optional<BuildTool> detectBuildTool(@Nullable String gradleBuild, @Nullable String mavenPom) {
        if (gradleBuild != null && !aresVersionPins(gradleBuild, BuildTool.GRADLE).isEmpty()) {
            return Optional.of(BuildTool.GRADLE);
        }
        if (mavenPom != null && !aresVersionPins(mavenPom, BuildTool.MAVEN).isEmpty()) {
            return Optional.of(BuildTool.MAVEN);
        }
        return Optional.empty();
    }

    /**
     * The places that pin the Ares version in a build file. A pin inside a comment, e.g. {@code // aresVersion = '2.3.0'}, or inside a Gradle string, e.g.
     * {@code "aresVersion = '2.3.0'"}, has no effect on the build, so it is neither read nor replaced.
     *
     * @param content   the content of the build file
     * @param buildTool the build tool the file belongs to
     * @return the pins outside comments and strings, in the order of the file
     */
    private static List<MatchResult> aresVersionPins(String content, BuildTool buildTool) {
        boolean[] noCode = buildTool == BuildTool.GRADLE ? BuildFileSyntax.gradleCommentsAndStrings(content) : BuildFileSyntax.mavenComments(content);
        return versionPattern(buildTool).matcher(content).results().filter(pin -> !noCode[pin.start()]).toList();
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the Ares version pinned in the build file, if any
     */
    public static Optional<String> readAresVersion(Path repositoryRoot) {
        return parseAresVersion(readFileIfPresent(repositoryRoot.resolve(GRADLE_BUILD_FILE)).orElse(null),
                readFileIfPresent(repositoryRoot.resolve(MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * Reads the Ares version from build file contents, e.g. as stored in a commit, without needing a working copy.
     *
     * @param gradleBuild content of {@code build.gradle}, if the repository has one
     * @param mavenPom    content of {@code pom.xml}, if the repository has one
     * @return the Ares version pinned in the build file, if any
     */
    public static Optional<String> parseAresVersion(@Nullable String gradleBuild, @Nullable String mavenPom) {
        return detectBuildTool(gradleBuild, mavenPom).map(buildTool -> {
            String content = buildTool == BuildTool.GRADLE ? gradleBuild : mavenPom;
            return aresVersionPins(content, buildTool).getFirst().group(2).trim();
        });
    }

    /**
     * Pins the given Ares version in the build file of the repository.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param aresVersion    the Ares version to pin
     * @return whether the build file changed
     */
    public static boolean writeAresVersion(Path repositoryRoot, String aresVersion) {
        Optional<BuildTool> buildTool = detectBuildTool(repositoryRoot);
        if (buildTool.isEmpty()) {
            return false;
        }
        Path buildFile = buildFile(repositoryRoot, buildTool.get());
        String content = readFile(buildFile);
        return writeIfChanged(buildFile, content, withAresVersion(content, buildTool.get(), aresVersion));
    }

    /** Replaces every Ares version pinned in code, not in comments, of the build file content. */
    private static String withAresVersion(String content, BuildTool buildTool, String aresVersion) {
        StringBuilder updated = new StringBuilder();
        int copiedUntil = 0;
        for (MatchResult pin : aresVersionPins(content, buildTool)) {
            updated.append(content, copiedUntil, pin.start()).append(pin.group(1)).append(aresVersion).append(pin.group(3));
            copiedUntil = pin.end();
        }
        return updated.append(content, copiedUntil, content.length()).toString();
    }

    /**
     * A build file whose pinned Ares version changes.
     *
     * @param path   the path of the build file relative to the repository root
     * @param before its content before
     * @param after  its content with the other version pinned
     */
    public record BuildFileChange(String path, String before, String after) {
    }

    /**
     * Pins another Ares version in the content of the build file, like {@link #writeAresVersion(Path, String)} does in a checkout.
     *
     * @param gradleBuild the content of {@code build.gradle}; null if there is none
     * @param mavenPom    the content of {@code pom.xml}; null if there is none
     * @param aresVersion the version to pin
     * @return the build file before and after; empty if no build file pins a version
     */
    public static Optional<BuildFileChange> pinAresVersion(@Nullable String gradleBuild, @Nullable String mavenPom, String aresVersion) {
        return detectBuildTool(gradleBuild, mavenPom).map(buildTool -> {
            String content = buildTool == BuildTool.GRADLE ? gradleBuild : mavenPom;
            return new BuildFileChange(buildTool == BuildTool.GRADLE ? GRADLE_BUILD_FILE : MAVEN_BUILD_FILE, content, withAresVersion(content, buildTool, aresVersion));
        });
    }

    /**
     * Writes a policy file. The path must have passed {@link #validatePolicyPath(String)}; it is checked again against the checkout, so neither a symbolic
     * link nor a path outside the repository is ever written.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the validated path of the policy file relative to the root
     * @param policy         the policy content
     */
    public static void writePolicy(Path repositoryRoot, String policyPath, String policy) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path file = root.resolve(policyPath).normalize();
        if (validatePolicyPath(policyPath).isEmpty() || !file.startsWith(root) || hasSymbolicLinkOnPath(root, file)
                || (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("Refusing to write the policy file " + policyPath);
        }
        writeIfChanged(file, readFileIfPresent(file).orElse(null), policy);
    }

    /**
     * Deletes a policy file, if present. A path that leaves the repository or passes a symbolic link is left alone.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the validated path of the policy file relative to the root
     */
    public static void deletePolicyFile(Path repositoryRoot, String policyPath) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path file = root.resolve(policyPath).normalize();
        if (!file.startsWith(root) || hasSymbolicLinkOnPath(root, file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.delete(file);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes a file when the Security Framework is deactivated, if it has the shape of a policy, see {@link SecurityPolicyYaml#isPolicyShaped(String)}. A
     * policy goes even if the pinned Ares version would refuse it; any other file stays, e.g. a build file, a YAML configuration or a file whose YAML is broken
     * that an annotation points to.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the validated path of the file relative to the root
     */
    public static void deletePolicyFileIfPolicy(Path repositoryRoot, String policyPath) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        PolicyFileContent content = readPolicyFile(root, policyPath);
        if (content.problem() == null && content.content() != null && SecurityPolicyYaml.isPolicyShaped(content.content())) {
            deletePolicyFile(root, policyPath);
        }
    }

    /**
     * Switches every Ares {@code @Policy} annotation in the Java files of the repository on or off. Only the {@code activated} flag changes: the policy file
     * an annotation points to and its other arguments are kept, so an instructor's custom policy path survives a deactivate/activate cycle and the
     * cycle restores every annotation exactly.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param active         whether the annotations should enforce their policy
     * @return the number of files that changed
     */
    public static int setPolicyAnnotationsActive(Path repositoryRoot, boolean active) {
        return setPolicyAnnotationsActive(repositoryRoot, (_, _) -> active);
    }

    /**
     * Switches the Ares {@code @Policy} annotations of each Java file on or off, as decided per annotation: for the annotation of the test class and for
     * the one of each test method, so that a switched-off test method stays so when its class is switched on again. A test class is identified by its
     * fully qualified name, so a class that replaced another one in the same file is not mistaken for it. The Security Framework only changes
     * repositories in which every file has at most one relevant top-level type, see {@link AresTestSources}; any other annotation of a file follows the
     * decision for its class.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param decision       whether each annotation should enforce its policy
     * @return the number of files that changed
     */
    public static int setPolicyAnnotationsActive(Path repositoryRoot, AnnotationDecision decision) {
        int changedFiles = 0;
        for (Path javaFile : javaFiles(repositoryRoot)) {
            String content = readFile(javaFile);
            String path = repositoryRoot.relativize(javaFile).toString().replace('\\', '/');
            Optional<AresTestSources.TypeDeclaration> testClass = AresTestSources.relevantType(AresTestSources.analyze(path, content));
            Map<Integer, String> methodsByOffset = new HashMap<>();
            testClass.ifPresent(type -> type.methodPolicies().forEach((method, annotation) -> methodsByOffset.put(annotation.start(), method)));
            String qualifiedName = testClass.map(AresTestSources.TypeDeclaration::qualifiedName).orElse(null);
            String updated = AresPolicyAnnotations.setActive(content, annotation -> decision.isActive(qualifiedName, methodsByOffset.get(annotation.start())));
            if (writeIfChanged(javaFile, content, updated)) {
                changedFiles++;
            }
        }
        return changedFiles;
    }

    /**
     * Adds test classes to the list of test classes of a policy file, see {@link SecurityPolicyYaml#addTestClasses(String, List)}.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the validated path of the policy file relative to the root
     * @param testClasses    the fully qualified names to add
     * @return whether the list now contains the classes; false if the file cannot be read or its list cannot be located, in which case it is unchanged
     */
    public static boolean addTestClassesToPolicy(Path repositoryRoot, String policyPath, List<String> testClasses) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        PolicyFileContent content = readPolicyFile(root, policyPath);
        if (content.content() == null) {
            return false;
        }
        Optional<String> updated = SecurityPolicyYaml.addTestClasses(content.content(), testClasses);
        updated.ifPresent(policy -> writePolicy(root, policyPath, policy));
        return updated.isPresent();
    }

    /**
     * Takes the section of the permitted resource accesses from a policy of the Java templates, as written. It ends at the next line that is indented no
     * deeper than the section's key.
     *
     * @param templatePolicy the content of a template policy
     * @return the section ready for {@link #generateDefaultPolicy}; empty if the policy has no such section
     */
    public static Optional<String> extractResourcePermissions(String templatePolicy) {
        List<String> lines = templatePolicy.lines().toList();
        int keyLine = lines.indexOf(RESOURCE_PERMISSIONS_LINE);
        if (keyLine < 0) {
            return Optional.empty();
        }
        int keyIndentation = RESOURCE_PERMISSIONS_LINE.length() - RESOURCE_PERMISSIONS_LINE.stripLeading().length();
        StringBuilder section = new StringBuilder(RESOURCE_PERMISSIONS_LINE).append('\n');
        for (String line : lines.subList(keyLine + 1, lines.size())) {
            boolean endsSection = !BLANK_OR_COMMENT_LINE.matcher(line).matches() && line.length() - line.stripLeading().length() <= keyIndentation;
            if (endsSection) {
                break;
            }
            section.append(line).append('\n');
        }
        return Optional.of(section.toString());
    }

    /**
     * Generates a policy for a repository that has none: every class that contains tests is registered as a test class so Ares does not treat it as student
     * code.
     *
     * @param repositoryRoot      root of the checked-out exercise-tests repository
     * @param buildTool           the build tool of the repository
     * @param packageName         the package of the supervised student code, if the exercise has one
     * @param resourcePermissions the section of the permitted resource accesses, either {@link #NO_RESOURCE_PERMISSIONS} or one from
     *                                {@link #extractResourcePermissions}
     * @return the YAML content of the policy
     */
    public static String generateDefaultPolicy(Path repositoryRoot, BuildTool buildTool, Optional<String> packageName, String resourcePermissions) {
        StringBuilder policy = new StringBuilder();
        policy.append("# Ares 2 security policy for the tests of this repository, generated by Artemis when the Security Framework was activated.\n");
        policy.append("# Docs: https://ls1intum.github.io/Ares2/instructor/policy-reference\n");
        policy.append("# Add every new test class below, otherwise Ares treats it like student code and blocks it.\n");
        policy.append("thisPolicyFileCompliesToThePolicyVersion: 1\n");
        policy.append("regardingTheSupervisedCode:\n");
        policy.append("  theFollowingProgrammingLanguageConfigurationIsUsed: ").append(buildTool.aresLanguageConfiguration).append('\n');
        packageName.filter(name -> !name.isBlank()).ifPresent(name -> policy.append("  theSupervisedCodeUsesTheFollowingPackage: \"").append(name).append("\"\n"));
        List<String> testClasses = findTestClasses(repositoryRoot);
        if (testClasses.isEmpty()) {
            policy.append("  theFollowingClassesAreTestClasses: [ ]\n");
        }
        else {
            policy.append("  theFollowingClassesAreTestClasses:\n");
            testClasses.forEach(testClass -> policy.append("    - \"").append(testClass).append("\"\n"));
        }
        return policy.append(resourcePermissions).toString();
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the fully qualified names of all classes in the repository that contain tests, sorted
     */
    static List<String> findTestClasses(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        List<AresTestSources.Summary> summaries = javaSources(root).entrySet().stream().map(source -> AresTestSources.analyze(source.getKey(), source.getValue())).toList();
        return testClasses(summaries).stream().map(AresTestSources.TypeDeclaration::qualifiedName).sorted().toList();
    }

    /**
     * Lists the symbolic links of a checkout. Reading or rewriting a file follows a link, so a link committed to the repository could make a change of the
     * Security Framework read or modify a file outside the checkout with the server's permissions. The {@code .git} directory belongs to the server, not to
     * the repository content, and is not checked.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the symbolic links relative to the root, with forward slashes and sorted; empty if there are none
     */
    public static List<String> findSymbolicLinks(Path repositoryRoot) {
        Path gitDirectory = repositoryRoot.resolve(".git");
        List<String> links = new ArrayList<>();
        try {
            // walkFileTree does not follow links, so a link to a directory is reported like a link to a file and its target is never entered.
            Files.walkFileTree(repositoryRoot, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return directory.equals(gitDirectory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isSymbolicLink()) {
                        links.add(repositoryRoot.relativize(file).toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        links.sort(String::compareTo);
        return links;
    }

    /**
     * Collects the Java sources of a checkout. The directories {@link #isJavaSourcePath} ignores anyway, above all {@code .git}, are not entered: Git and JGit
     * create and delete lock files there at any time, e.g. {@code gc.log.lock} after a commit, and a file that disappears while the tree is walked would
     * otherwise fail the whole evaluation. A file elsewhere that disappears in between is skipped for the same reason.
     */
    private static List<Path> javaFiles(Path repositoryRoot) {
        Predicate<String> isFile = relativePath -> Files.isRegularFile(repositoryRoot.resolve(relativePath), LinkOption.NOFOLLOW_LINKS);
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(repositoryRoot, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return !directory.equals(repositoryRoot) && IGNORED_DIRECTORIES.contains(directory.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && isJavaSourcePath(repositoryRoot.relativize(file).toString().replace('\\', '/'), isFile)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                    if (exception instanceof NoSuchFileException) {
                        return FileVisitResult.CONTINUE;
                    }
                    throw exception;
                }
            });
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        files.sort(Path::compareTo);
        return files;
    }

    private static Path buildFile(Path repositoryRoot, BuildTool buildTool) {
        return repositoryRoot.resolve(buildTool == BuildTool.GRADLE ? GRADLE_BUILD_FILE : MAVEN_BUILD_FILE);
    }

    private static Pattern versionPattern(BuildTool buildTool) {
        return buildTool == BuildTool.GRADLE ? GRADLE_ARES_VERSION : MAVEN_ARES_VERSION;
    }

    private static Optional<String> readFileIfPresent(Path file) {
        return Files.isRegularFile(file) ? Optional.of(readFile(file)) : Optional.empty();
    }

    static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean writeIfChanged(Path file, String previousContent, String newContent) {
        if (newContent.equals(previousContent)) {
            return false;
        }
        try {
            FileUtils.writeStringToFile(file.toFile(), newContent, StandardCharsets.UTF_8);
            return true;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
