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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

/**
 * Reads and rewrites the Ares 2 security setup inside a checked-out exercise-tests repository.
 * <p>
 * The Java templates ship Ares 2 already enabled: the test classes carry {@code @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")}, the
 * repository root contains {@code SecurityPolicy.yaml}, and the build file pins the Ares version ({@code aresVersion} in Gradle, {@code ares.version} in
 * Maven). This class is the single place that knows that layout:
 * <ul>
 * <li>the Security Framework is <em>active</em> when {@code SecurityPolicy.yaml} exists,</li>
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

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final Pattern TEST_CLASS_MARKER = Pattern.compile("@(org\\.junit\\.jupiter\\.api\\.)?Test\\b|@(de\\.tum\\.cit\\.ase\\.ares\\.api\\.)?Policy\\b");

    static final String ACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"" + POLICY_FILE_NAME + "\")";

    /** How {@link #ACTIVE_ANNOTATION} reads after deactivation: only the activation flag changes, so reactivating restores the original annotation. */
    static final String INACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"" + POLICY_FILE_NAME + "\", activated = false)";

    /** Directories that never contain test sources of the exercise, wherever they appear. */
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", "node_modules");

    /**
     * Build output directories. They are only skipped next to a build file, i.e. at the root of a (sub)project, so a source package that happens to be
     * called {@code build} or {@code target} is still scanned.
     */
    private static final Set<String> BUILD_OUTPUT_DIRECTORIES = Set.of("build", "target");

    private static final Set<String> BUILD_FILES = Set.of(GRADLE_BUILD_FILE, "build.gradle.kts", MAVEN_BUILD_FILE);

    private static final Pattern JAVA_FILE_SUFFIX = Pattern.compile("\\.java$");

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
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return whether the repository contains the Ares security policy file, i.e. whether the Security Framework is active
     */
    public static boolean isPolicyPresent(Path repositoryRoot) {
        return Files.isRegularFile(repositoryRoot.resolve(POLICY_FILE_NAME));
    }

    /**
     * Whether Ares enforces a policy for the tests of the repository. This is the single rule for the state of the Security Framework:
     * <ul>
     * <li>if a test switches its {@code @Policy} annotation off, the framework is off, because that test runs without a sandbox;</li>
     * <li>otherwise, if the tests have {@code @Policy} annotations, the framework is on if one of them enforces a policy: either Ares's strict default (no
     * policy file given) or a policy file that exists, such as an instructor's own one;</li>
     * <li>without any annotation, the framework is on if {@link #POLICY_FILE_NAME} exists.</li>
     * </ul>
     *
     * @param javaSources the contents of the Java files of the repository
     * @param fileExists  whether a path, relative to the repository root, is a file of the repository
     * @return whether the Security Framework is active
     */
    public static boolean isEnforcing(Iterable<String> javaSources, Predicate<String> fileExists) {
        boolean anyAnnotation = false;
        boolean anyEnforcingAnnotation = false;
        for (String javaSource : javaSources) {
            for (AresPolicyAnnotations.Annotation annotation : AresPolicyAnnotations.find(javaSource)) {
                if (!annotation.isActivated()) {
                    return false;
                }
                anyAnnotation = true;
                anyEnforcingAnnotation |= annotation.policyPath().map(fileExists::test).orElse(true);
            }
        }
        return anyAnnotation ? anyEnforcingAnnotation : fileExists.test(POLICY_FILE_NAME);
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository; it may be relative or contain {@code .} and {@code ..}, e.g. below a relative
     *                           {@code artemis.temp-path}
     * @return whether the Security Framework is active, see {@link #isEnforcing(Iterable, Predicate)}
     */
    public static boolean isEnforcing(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return isEnforcing(javaFiles(root).stream().map(SecurityPolicyRepositoryFiles::readFile).toList(), path -> isRepositoryFile(root, path));
    }

    /**
     * @param root the absolute, normalized repository root
     * @param path a path relative to the root
     * @return whether the path is a regular file inside the repository; a path that leaves the repository or is not valid is none
     */
    private static boolean isRepositoryFile(Path root, String path) {
        try {
            Path file = root.resolve(path).normalize();
            return file.startsWith(root) && Files.isRegularFile(file);
        }
        catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return whether a test points its {@code @Policy} annotation to {@link #POLICY_FILE_NAME}, or no test has one, so that file is the one Ares reads
     */
    public static boolean isPolicyFileUsed(Path repositoryRoot) {
        List<AresPolicyAnnotations.Annotation> annotations = javaFiles(repositoryRoot).stream().flatMap(javaFile -> AresPolicyAnnotations.find(readFile(javaFile)).stream())
                .toList();
        return annotations.isEmpty() || annotations.stream().anyMatch(annotation -> annotation.policyPath().filter(POLICY_FILE_NAME::equals).isPresent());
    }

    /**
     * @param path a path inside the repository, relative to its root
     * @return whether the path is a Java file outside the tool directories, i.e. one that {@link #setPolicyAnnotationsActive} would also rewrite
     */
    public static boolean isJavaSourcePath(String path) {
        if (!path.endsWith(".java")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (IGNORED_DIRECTORIES.contains(segment)) {
                return false;
            }
        }
        return true;
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
     * Writes the policy file.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policy         the policy content
     */
    public static void writePolicy(Path repositoryRoot, String policy) {
        writeIfChanged(repositoryRoot.resolve(POLICY_FILE_NAME), readFileIfPresent(repositoryRoot.resolve(POLICY_FILE_NAME)).orElse(null), policy);
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
        int changedFiles = 0;
        for (Path javaFile : javaFiles(repositoryRoot)) {
            String content = readFile(javaFile);
            if (writeIfChanged(javaFile, content, AresPolicyAnnotations.setActive(content, active))) {
                changedFiles++;
            }
        }
        return changedFiles;
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
        List<String> testClasses = new ArrayList<>();
        for (Path javaFile : javaFiles(repositoryRoot)) {
            String content = AresPolicyAnnotations.mask(readFile(javaFile));
            if (!TEST_CLASS_MARKER.matcher(content).find()) {
                continue;
            }
            String simpleName = JAVA_FILE_SUFFIX.matcher(javaFile.getFileName().toString()).replaceFirst("");
            Matcher packageMatcher = PACKAGE_DECLARATION.matcher(content);
            testClasses.add(packageMatcher.find() ? packageMatcher.group(1) + "." + simpleName : simpleName);
        }
        testClasses.sort(String::compareTo);
        return testClasses;
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
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.getFileName().toString().endsWith(".java")
                    && isScannedSource(repositoryRoot, path)).sorted().toList();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return false if the file lies below a directory that holds tool or build output instead of sources
     */
    private static boolean isScannedSource(Path repositoryRoot, Path file) {
        Path directory = repositoryRoot;
        Path relativeDirectory = repositoryRoot.relativize(file.getParent());
        for (Path segment : relativeDirectory) {
            String name = segment.toString();
            if (name.isEmpty()) {
                continue;
            }
            if (IGNORED_DIRECTORIES.contains(name) || (BUILD_OUTPUT_DIRECTORIES.contains(name) && containsBuildFile(directory))) {
                return false;
            }
            directory = directory.resolve(name);
        }
        return true;
    }

    private static boolean containsBuildFile(Path directory) {
        return BUILD_FILES.stream().anyMatch(buildFile -> Files.isRegularFile(directory.resolve(buildFile)));
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
