package de.tum.cit.aet.artemis.programming.service.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final String GRADLE_BUILD_FILE = "build.gradle";

    private static final String MAVEN_BUILD_FILE = "pom.xml";

    private static final Pattern GRADLE_ARES_VERSION = Pattern.compile("(aresVersion\\s*=\\s*['\"])([^'\"]+)(['\"])");

    private static final Pattern MAVEN_ARES_VERSION = Pattern.compile("(<ares\\.version>)([^<]+)(</ares\\.version>)");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:");

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
     * the tests;</li>
     * <li>{@code ACTIVE} if at least one test class has an active {@code @Policy} annotation;</li>
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

        List<AresTestSources.TypeDeclaration> testClasses = testClasses(summaries);
        if (!usesAres1) {
            reasons.addAll(testClassReasons(testClasses));
        }
        if (!reasons.isEmpty()) {
            return new SecurityFrameworkState(SecurityActivationStatus.UNSUPPORTED, List.of(), List.of(), List.copyOf(reasons));
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
        }
        List<SecurityFrameworkState.PolicyFile> policyFiles = new ArrayList<>();
        classesByPolicyPath.forEach((path, referencedBy) -> policyFiles.add(policyFile(path, referencedBy.stream().sorted().toList(), reader)));

        SecurityActivationStatus status;
        if (policyFiles.stream().anyMatch(policyFile -> policyFile.problem() != null && policyFile.problem().breaksTheSandbox())) {
            status = SecurityActivationStatus.MISCONFIGURED;
        }
        else {
            status = anyActive ? SecurityActivationStatus.ACTIVE : SecurityActivationStatus.INACTIVE;
        }
        disabledTestClasses.sort(String::compareTo);
        return new SecurityFrameworkState(status, List.copyOf(disabledTestClasses), List.copyOf(policyFiles), List.of());
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
    private static List<AresTestSources.TypeDeclaration> testClasses(List<AresTestSources.Summary> summaries) {
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
        for (AresTestSources.TypeDeclaration testClass : testClasses) {
            if (testClass.policies().size() != 1) {
                reasons.add(new SecurityFrameworkState.UnsupportedReason(testClass.qualifiedName(),
                        testClass.policies().isEmpty() ? SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY
                                : SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS));
            }
            if (!testClass.registersSandbox()) {
                reasons.add(new SecurityFrameworkState.UnsupportedReason(testClass.qualifiedName(), SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES));
            }
        }
        return reasons;
    }

    private static SecurityFrameworkState.PolicyFile policyFile(String path, List<String> referencedBy, PolicyFileReader reader) {
        Optional<String> validPath = validatePolicyPath(path);
        if (validPath.isEmpty()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.INVALID_PATH, List.of());
        }
        PolicyFileContent content = reader.read(validPath.get());
        if (content.problem() != null || content.content() == null) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, content.problem(), List.of());
        }
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicyYaml.read(content.content());
        if (!policy.valid()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE, List.of());
        }
        if (policy.testClasses().isEmpty()) {
            return new SecurityFrameworkState.PolicyFile(path, referencedBy, SecurityFrameworkState.PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND, List.of());
        }
        List<String> missing = referencedBy.stream().filter(testClass -> !policy.testClasses().get().contains(testClass)).toList();
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
        catch (InvalidPathException e) {
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
        catch (IOException e) {
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

    private static BuildFiles buildFiles(Path repositoryRoot) {
        return new BuildFiles(readFileIfPresent(repositoryRoot.resolve(GRADLE_BUILD_FILE)).orElse(null), readFileIfPresent(repositoryRoot.resolve(MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * @param javaSources the Java sources of a repository by their path relative to its root
     * @return for every source whose test class has an evaluable {@code @Policy} annotation whether it is switched on, by the path of the source
     */
    public static Map<String, Boolean> annotationStates(Map<String, String> javaSources) {
        Map<String, Boolean> states = new TreeMap<>();
        javaSources.forEach((path, source) -> AresTestSources.relevantType(AresTestSources.analyze(path, source)).filter(type -> type.policies().size() == 1)
                .map(type -> type.policies().getFirst()).filter(AresPolicyAnnotations.Annotation::isEvaluable).ifPresent(annotation -> states.put(path, annotation.isActivated())));
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
    private static Optional<BuildTool> detectBuildTool(@Nullable String gradleBuild, @Nullable String mavenPom) {
        if (gradleBuild != null && GRADLE_ARES_VERSION.matcher(gradleBuild).find()) {
            return Optional.of(BuildTool.GRADLE);
        }
        if (mavenPom != null && MAVEN_ARES_VERSION.matcher(mavenPom).find()) {
            return Optional.of(BuildTool.MAVEN);
        }
        return Optional.empty();
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
            Matcher matcher = versionPattern(buildTool).matcher(buildTool == BuildTool.GRADLE ? gradleBuild : mavenPom);
            return matcher.find() ? matcher.group(2).trim() : null;
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
        String updated = versionPattern(buildTool.get()).matcher(content).replaceAll(match -> Matcher.quoteReplacement(match.group(1) + aresVersion + match.group(3)));
        return writeIfChanged(buildFile, content, updated);
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
     * Deletes the policy file, if present.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     */
    public static void deletePolicy(Path repositoryRoot) {
        try {
            Files.deleteIfExists(repositoryRoot.resolve(POLICY_FILE_NAME));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
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
        return setPolicyAnnotationsActive(repositoryRoot, _ -> active);
    }

    /**
     * Switches the Ares {@code @Policy} annotations of each Java file on or off, as decided per file. The Security Framework only changes repositories in
     * which every file has at most one relevant top-level type, see {@link AresTestSources}, so one decision per file is one decision per test class.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param activeForPath  whether the annotations of the file at a path, relative to the root with forward slashes, should enforce their policy
     * @return the number of files that changed
     */
    public static int setPolicyAnnotationsActive(Path repositoryRoot, Predicate<String> activeForPath) {
        int changedFiles = 0;
        for (Path javaFile : javaFiles(repositoryRoot)) {
            String content = readFile(javaFile);
            boolean active = activeForPath.test(repositoryRoot.relativize(javaFile).toString().replace('\\', '/'));
            if (writeIfChanged(javaFile, content, AresPolicyAnnotations.setActive(content, active))) {
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
     * Generates the default policy for the repository: no resource access is permitted, and every class that contains tests is registered as a test class
     * so Ares does not treat it as student code.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param buildTool      the build tool of the repository
     * @param packageName    the package of the supervised student code, if the exercise has one
     * @return the YAML content of the policy
     */
    public static String generateDefaultPolicy(Path repositoryRoot, BuildTool buildTool, Optional<String> packageName) {
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
        policy.append("  theFollowingResourceAccessesArePermitted:\n");
        for (String resource : List.of("regardingFileSystemInteractions", "regardingNetworkConnections", "regardingCommandExecutions", "regardingThreadCreations",
                "regardingPackageImports", "regardingTimeouts")) {
            policy.append("    ").append(resource).append(": [ ]\n");
        }
        return policy.toString();
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

    private static List<Path> javaFiles(Path repositoryRoot) {
        try (Stream<Path> files = Files.walk(repositoryRoot)) {
            Predicate<String> isFile = relativePath -> Files.isRegularFile(repositoryRoot.resolve(relativePath), LinkOption.NOFOLLOW_LINKS);
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && isJavaSourcePath(repositoryRoot.relativize(path).toString().replace('\\', '/'), isFile))
                    .sorted().toList();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean writeIfChanged(Path file, String previousContent, String newContent) {
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
