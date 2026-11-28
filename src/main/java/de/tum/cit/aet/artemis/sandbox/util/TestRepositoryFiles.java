package de.tum.cit.aet.artemis.sandbox.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.sandbox.domain.AresApiUse;
import de.tum.cit.aet.artemis.sandbox.domain.BuildFiles;
import de.tum.cit.aet.artemis.sandbox.domain.BuildTool;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFile;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileContent;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReason;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;

/**
 * Reads the Ares 2 security setup of an exercise-tests repository from the content of its files, wherever they come from.
 * <p>
 * The Java templates ship Ares 2 already enabled: the test classes carry {@code @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")}, the
 * repository root contains {@code SecurityPolicy.yaml}, and the build file pins the Ares version ({@code aresVersion} in Gradle, {@code ares.version} in
 * Maven). This class is the single place that knows that layout: the state of the sandbox is what Ares applies when the tests run, see
 * {@link #evaluate(Map, Function, BuildFiles)}. {@link TestRepositoryCommitFiles} applies it to a commit of the repository.
 * <p>
 * The Ares library itself stays on the test classpath, because the template tests also use its non-security APIs (structural tests, reflection helpers,
 * timeouts). All methods are side-effect free.
 */
public final class TestRepositoryFiles {

    /** Name of the policy file at the root of the exercise-tests repository, as referenced by the template test classes. */
    public static final String POLICY_FILE_NAME = "SecurityPolicy.yaml";

    static final String GRADLE_BUILD_FILE = "build.gradle";

    static final String MAVEN_BUILD_FILE = "pom.xml";

    private static final Pattern WINDOWS_DRIVE = Pattern.compile("[A-Za-z]:");

    /** Directories that never contain test sources of the exercise, wherever they appear. */
    static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", "node_modules");

    /**
     * Build output directories. They are only skipped next to a build file, i.e. at the root of a (sub)project, so a source package that happens to be
     * called {@code build} or {@code target} is still scanned.
     */
    static final Set<String> BUILD_OUTPUT_DIRECTORIES = Set.of("build", "target");

    private static final Set<String> BUILD_FILES = Set.of(GRADLE_BUILD_FILE, "build.gradle.kts", MAVEN_BUILD_FILE);

    private TestRepositoryFiles() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Determines the Security Framework state of a tests repository, as Ares applies it when the tests run. This is the single rule for the state:
     * <ul>
     * <li>{@code UNSUPPORTED} if the repository uses Ares in a way the Security Framework cannot manage reliably, see
     * {@link UnsupportedReasonType};</li>
     * <li>{@code MISCONFIGURED} if an active {@code @Policy} annotation points to a policy file that is missing, invalid or unreadable, because Ares then fails
     * the tests. A repository that pins the Ares version {@link SecurityPolicySchema} was verified against has its policy files checked against the rules of
     * that version; for any other version a file only has to be a YAML mapping, so {@code CONFIGURED} does not establish that this Ares version accepts it;</li>
     * <li>{@code CONFIGURED} if at least one test class has an active {@code @Policy} annotation;</li>
     * <li>{@code INACTIVE} otherwise.</li>
     * </ul>
     * Only the policy files of active annotations are read, because Ares does not read the others either.
     *
     * @param javaSources the Java sources of the repository by their path relative to its root, see {@link #isJavaSourcePath(String, Predicate)}
     * @param reader      reads a policy file of the repository by its validated path, see {@link #validatePolicyPath(String)}; a checkout and a committed tree
     *                        read differently, but both reject everything that is not a regular file and check the size before reading
     * @param buildFiles  the build files at the root of the repository
     * @return the state
     */
    public static SandboxState evaluate(Map<String, String> javaSources, Function<String, PolicyFileContent> reader, BuildFiles buildFiles) {
        List<AresTestSources.Summary> summaries = javaSources.entrySet().stream().map(source -> AresTestSources.analyze(source.getKey(), source.getValue())).toList();
        List<AresApiUse> aresApiUses = summaries.stream().flatMap(summary -> summary.aresApis().stream().map(api -> new AresApiUse(summary.path(), api)))
                .sorted(Comparator.comparing(AresApiUse::location).thenComparing(AresApiUse::api)).toList();
        List<AresTestSources.TypeDeclaration> testClasses = testClasses(summaries);
        List<UnsupportedReason> reasons = unsupportedReasons(summaries, testClasses, buildFiles);
        if (!reasons.isEmpty()) {
            return new SandboxState(SandboxStatus.UNSUPPORTED, List.of(), List.of(), List.copyOf(reasons), aresApiUses);
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
        boolean verifiedAresVersion = parseAresVersion(buildFiles.gradleBuild(), buildFiles.mavenPom()).filter(SecurityPolicySchema.VERIFIED_ARES_VERSION::equals).isPresent();
        List<PolicyFile> policyFiles = new ArrayList<>();
        classesByPolicyPath.forEach((path, referencedBy) -> policyFiles.add(policyFile(path, referencedBy.stream().sorted().toList(), reader, verifiedAresVersion)));

        SandboxStatus status;
        if (policyFiles.stream().anyMatch(policyFile -> policyFile.problem() != null && policyFile.problem().breaksTheSandbox())) {
            status = SandboxStatus.MISCONFIGURED;
        }
        else {
            status = anyActive ? SandboxStatus.CONFIGURED : SandboxStatus.INACTIVE;
        }
        disabledTestClasses.sort(String::compareTo);
        return new SandboxState(status, List.copyOf(disabledTestClasses), List.copyOf(policyFiles), List.of(), aresApiUses);
    }

    /**
     * @return why the Security Framework cannot manage the repository, in this order: Ares 1, no pinned Ares version or one in a Maven profile, the problems of
     *         each source file, and the problems of the test classes; empty if it can manage it
     */
    private static List<UnsupportedReason> unsupportedReasons(List<AresTestSources.Summary> summaries, List<AresTestSources.TypeDeclaration> testClasses, BuildFiles buildFiles) {
        List<UnsupportedReason> reasons = new ArrayList<>();
        boolean usesAres1 = BuildFileSyntax.declaresAres1Dependency(buildFiles) || summaries.stream().anyMatch(AresTestSources.Summary::usesAres1);
        if (usesAres1) {
            reasons.add(new UnsupportedReason(buildFiles.gradleBuild() != null ? GRADLE_BUILD_FILE : MAVEN_BUILD_FILE, UnsupportedReasonType.ARES_1));
        }
        Optional<BuildTool> buildTool = detectBuildTool(buildFiles.gradleBuild(), buildFiles.mavenPom());
        // A Gradle build that assigns the Ares version decides it, so a pin of the POM next to it, also one in a profile, does not matter.
        boolean gradleDecides = gradleAssignsAresVersion(buildFiles.gradleBuild());
        boolean pinsInProfile = !gradleDecides && buildFiles.mavenPom() != null && BuildFileSyntax.pinsInMavenProfile(buildFiles.mavenPom());
        if (pinsInProfile) {
            reasons.add(new UnsupportedReason(MAVEN_BUILD_FILE, UnsupportedReasonType.ARES_VERSION_IN_MAVEN_PROFILE));
        }
        else if (buildTool.isEmpty()) {
            reasons.add(
                    new UnsupportedReason(buildFiles.mavenPom() != null && !gradleDecides ? MAVEN_BUILD_FILE : GRADLE_BUILD_FILE, UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        }
        summaries.forEach(summary -> summary.problems().forEach(problem -> reasons.add(new UnsupportedReason(summary.path(), problem))));
        if (!usesAres1) {
            reasons.addAll(testClassReasons(testClasses));
        }
        return reasons;
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

    private static List<UnsupportedReason> testClassReasons(List<AresTestSources.TypeDeclaration> testClasses) {
        if (!testClasses.isEmpty() && testClasses.stream().noneMatch(testClass -> testClass.registersSandbox() || !testClass.policies().isEmpty())) {
            return List.of(new UnsupportedReason(testClasses.getFirst().qualifiedName(), UnsupportedReasonType.NO_ARES_2));
        }
        List<UnsupportedReason> reasons = new ArrayList<>();
        for (AresTestSources.TypeDeclaration testClass : testClasses) {
            if (testClass.policies().size() != 1) {
                reasons.add(new UnsupportedReason(testClass.qualifiedName(),
                        testClass.policies().isEmpty() ? UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY : UnsupportedReasonType.UNEVALUABLE_ARGUMENTS));
            }
            if (!testClass.registersSandbox()) {
                reasons.add(new UnsupportedReason(testClass.qualifiedName(), UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES));
            }
        }
        return reasons;
    }

    private static PolicyFile policyFile(String path, List<String> referencedBy, Function<String, PolicyFileContent> reader, boolean verifiedAresVersion) {
        Optional<String> validPath = validatePolicyPath(path);
        if (validPath.isEmpty()) {
            return new PolicyFile(path, referencedBy, PolicyFileProblem.INVALID_PATH, List.of());
        }
        PolicyFileContent content = reader.apply(validPath.get());
        if (content.problem() != null || content.content() == null) {
            return new PolicyFile(path, referencedBy, content.problem(), List.of());
        }
        SecurityPolicyYaml.ReadPolicy policy = verifiedAresVersion ? SecurityPolicySchema.read(content.content()) : SecurityPolicyYaml.read(content.content());
        if (!policy.valid()) {
            return new PolicyFile(path, referencedBy, PolicyFileProblem.UNPARSEABLE, List.of());
        }
        if (policy.testClasses().isEmpty()) {
            return new PolicyFile(path, referencedBy, PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND, List.of());
        }
        List<String> missing = referencedBy.stream().filter(testClass -> !policy.testClasses().get().contains(testClass)).toList();
        return new PolicyFile(path, referencedBy, null, missing);
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
            // A backslash separates names as on Windows, so a .. behind it is resolved here, before the checks below, and not only by the checkout.
            normalized = Path.of(path.replace('\\', '/')).normalize().toString().replace('\\', '/');
        }
        catch (InvalidPathException _) {
            return Optional.empty();
        }
        // A file system that ignores the case, e.g. on macOS or Windows, also resolves .GIT to the Git directory.
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("..") || normalized.startsWith("../") || lowerCase.equals(".git") || lowerCase.startsWith(".git/")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    /**
     * @param javaSources the Java sources of a repository by their path relative to its root
     * @return for every test class with an evaluable {@code @Policy} annotation whether it is switched on, by the fully qualified name of the class
     */
    public static Map<String, Boolean> annotationStates(Map<String, String> javaSources) {
        Map<String, Boolean> states = new TreeMap<>();
        javaSources.forEach((path, source) -> AresTestSources.relevantType(AresTestSources.analyze(path, source))
                .filter(type -> type.policies().size() == 1 && type.policies().getFirst().isEvaluable())
                .ifPresent(type -> states.put(type.qualifiedName(), type.policies().getFirst().isActivated())));
        return states;
    }

    /**
     * Decides which files are scanned as Java sources. Both the scan of a checkout and the scan of a committed tree use it, so both see the same sources.
     *
     * @param path   a regular file of the repository, relative to its root, with forward slashes
     * @param isFile whether a path, relative to the root, is a regular file of the repository
     * @return whether the path is a Java file outside the tool and build output directories, i.e. one that {@link TestRepositoryCheckout#setPolicyAnnotationsActive} would also
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
     * @param gradleBuild content of {@code build.gradle}, if the repository has one
     * @param mavenPom    content of {@code pom.xml}, if the repository has one
     * @return the build tool whose build file pins an Ares version; a Gradle build that assigns the version decides it, so if Artemis cannot read its pins,
     *         e.g. pins of different versions, there is none, as the pin of a POM next to it neither tells nor changes the version the Gradle build uses
     */
    static Optional<BuildTool> detectBuildTool(@Nullable String gradleBuild, @Nullable String mavenPom) {
        if (gradleAssignsAresVersion(gradleBuild)) {
            return BuildFileSyntax.aresVersionPins(gradleBuild, BuildTool.GRADLE).isEmpty() ? Optional.empty() : Optional.of(BuildTool.GRADLE);
        }
        if (mavenPom != null && !BuildFileSyntax.aresVersionPins(mavenPom, BuildTool.MAVEN).isEmpty()) {
            return Optional.of(BuildTool.MAVEN);
        }
        return Optional.empty();
    }

    private static boolean gradleAssignsAresVersion(@Nullable String gradleBuild) {
        return gradleBuild != null && BuildFileSyntax.assignsGradleAresVersion(gradleBuild);
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
            return BuildFileSyntax.aresVersionPins(content, buildTool).getFirst().group(2).trim();
        });
    }
}
