package de.tum.cit.aet.artemis.sandbox.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import de.tum.cit.aet.artemis.sandbox.domain.BuildFiles;
import de.tum.cit.aet.artemis.sandbox.domain.BuildTool;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileContent;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;

/**
 * Reads and rewrites the Ares 2 security setup in a checkout of an exercise-tests repository, see {@link TestRepositoryFiles} for the layout.
 * <ul>
 * <li>deactivating deletes the policy file and switches every {@code @Policy} annotation to {@code activated = false}, so Ares stops enforcing a sandbox
 * (removing the annotation alone would not do that, because Ares then applies its strict default policy),</li>
 * <li>activating writes a policy file and switches the annotations back on.</li>
 * </ul>
 * All methods are side-effect free apart from the files they are asked to write.
 */
public final class TestRepositoryCheckout {

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

    private static final Pattern BLANK_OR_COMMENT_LINE = Pattern.compile("^[ \\t]*(#.*)?$");

    private TestRepositoryCheckout() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository; it may be relative or contain {@code .} and {@code ..}, e.g. below a relative
     *                           {@code artemis.temp-path}
     * @return the state of the checkout, see {@link TestRepositoryFiles#evaluate(Map, Function, BuildFiles)}
     */
    public static SandboxState evaluate(Path repositoryRoot) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        return TestRepositoryFiles.evaluate(javaSources(root), path -> readPolicyFile(root, path), buildFiles(root));
    }

    /**
     * Reads a policy file of a checkout without following symbolic links, neither for the file nor for a directory above it.
     *
     * @param root an absolute, normalized repository root
     * @param path a validated path relative to the root
     * @return the content or the problem
     */
    public static PolicyFileContent readPolicyFile(Path root, String path) {
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || blocksPolicyFile(root, file)) {
            return PolicyFileContent.failed(PolicyFileProblem.INVALID_PATH);
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return PolicyFileContent.failed(PolicyFileProblem.MISSING);
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return PolicyFileContent.failed(PolicyFileProblem.INVALID_PATH);
        }
        try {
            if (Files.size(file) > SecurityPolicyYaml.MAX_POLICY_SIZE) {
                return PolicyFileContent.failed(PolicyFileProblem.TOO_LARGE);
            }
            return PolicyFileContent.of(Files.readString(file, StandardCharsets.UTF_8));
        }
        catch (IOException _) {
            return PolicyFileContent.failed(PolicyFileProblem.UNPARSEABLE);
        }
    }

    /**
     * @return whether a component of the path below the root is a symbolic link, or a directory above the file is a file instead, e.g. {@code notes.txt} in
     *         {@code notes.txt/SecurityPolicy.yaml}, so no policy file can be at the path
     */
    private static boolean blocksPolicyFile(Path root, Path file) {
        for (Path current = file; current != null && current.startsWith(root) && !current.equals(root); current = current.getParent()) {
            boolean fileAsDirectory = !current.equals(file) && Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(current) || fileAsDirectory) {
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
        return new BuildFiles(readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.GRADLE_BUILD_FILE)).orElse(null),
                readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the build tool of the repository, if it has a Gradle or Maven build file that pins an Ares version
     */
    public static Optional<BuildTool> detectBuildTool(Path repositoryRoot) {
        return TestRepositoryFiles.detectBuildTool(readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.GRADLE_BUILD_FILE)).orElse(null),
                readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.MAVEN_BUILD_FILE)).orElse(null));
    }

    /**
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @return the Ares version pinned in the build file, if any
     */
    public static Optional<String> readAresVersion(Path repositoryRoot) {
        return TestRepositoryFiles.parseAresVersion(readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.GRADLE_BUILD_FILE)).orElse(null),
                readFileIfPresent(repositoryRoot.resolve(TestRepositoryFiles.MAVEN_BUILD_FILE)).orElse(null));
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
        StringBuilder updated = new StringBuilder();
        int copiedUntil = 0;
        for (MatchResult pin : BuildFileSyntax.aresVersionPins(content, buildTool.get())) {
            updated.append(content, copiedUntil, pin.start()).append(pin.group(1)).append(aresVersion).append(pin.group(3));
            copiedUntil = pin.end();
        }
        updated.append(content, copiedUntil, content.length());
        return writeIfChanged(buildFile, content, updated.toString());
    }

    /**
     * Writes a policy file. The path must have passed {@link TestRepositoryFiles#validatePolicyPath(String)}; it is checked again against the checkout, so neither a symbolic
     * link nor a path outside the repository is ever written.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the validated path of the policy file relative to the root
     * @param policy         the policy content
     */
    public static void writePolicy(Path repositoryRoot, String policyPath, String policy) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path file = root.resolve(policyPath).normalize();
        if (TestRepositoryFiles.validatePolicyPath(policyPath).isEmpty() || !file.startsWith(root) || blocksPolicyFile(root, file)
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
            Files.deleteIfExists(repositoryRoot.resolve(TestRepositoryFiles.POLICY_FILE_NAME));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Switches every Ares {@code @Policy} annotation in the Java files of the repository on or off. Only the {@code activated} flag changes: the policy file
     * an annotation points to and its other arguments are kept, so an instructor's custom policy path survives a deactivate/activate cycle, which restores
     * the policy and the activation of every annotation; see {@link AresPolicyAnnotations#setActive(String, boolean)} for how its spelling may change.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param active         whether the annotations should enforce their policy
     * @return the number of files that changed
     */
    public static int setPolicyAnnotationsActive(Path repositoryRoot, boolean active) {
        return setPolicyAnnotationsActive(repositoryRoot, (_, _) -> active);
    }

    /**
     * Switches the Ares {@code @Policy} annotations of each test class on or off, as decided per class. The Security Framework only changes repositories in
     * which every file has at most one relevant top-level type, see {@link AresTestSources}, so one decision per class is one decision per file. The
     * annotations of a file without such a type are switched on.
     *
     * @param repositoryRoot     root of the checked-out exercise-tests repository
     * @param activeForTestClass whether the annotations of the test class with a fully qualified name should enforce their policy
     * @return the number of files that changed
     */
    public static int setPolicyAnnotationsActive(Path repositoryRoot, Predicate<String> activeForTestClass) {
        return setPolicyAnnotationsActive(repositoryRoot,
                (path, content) -> AresTestSources.relevantType(AresTestSources.analyze(path, content)).map(type -> activeForTestClass.test(type.qualifiedName())).orElse(true));
    }

    /**
     * @param activeForFile whether the annotations of the file at a path, relative to the root with forward slashes, and with a content should enforce
     *                          their policy
     */
    private static int setPolicyAnnotationsActive(Path repositoryRoot, BiPredicate<String, String> activeForFile) {
        int changedFiles = 0;
        for (Path javaFile : javaFiles(repositoryRoot)) {
            String content = readFile(javaFile);
            boolean active = activeForFile.test(repositoryRoot.relativize(javaFile).toString().replace('\\', '/'), content);
            if (writeIfChanged(javaFile, content, AresPolicyAnnotations.setActive(content, active))) {
                changedFiles++;
            }
        }
        return changedFiles;
    }

    /**
     * Adds test classes to the list of test classes of a policy file, see {@link SecurityPolicyYaml#addTestClasses(String, List, boolean)}.
     *
     * @param repositoryRoot root of the checked-out exercise-tests repository
     * @param policyPath     the path of the policy file relative to the root as an annotation writes it, which is validated first
     * @param testClasses    the fully qualified names to add
     * @return whether the list now contains the classes; false if the file cannot be read or its list cannot be located, in which case it is unchanged
     */
    public static boolean addTestClassesToPolicy(Path repositoryRoot, String policyPath, List<String> testClasses) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        // The canonical path is the file the evaluation read, e.g. policies/SecurityPolicy.yaml for policies\SecurityPolicy.yaml.
        Optional<String> validatedPath = TestRepositoryFiles.validatePolicyPath(policyPath);
        PolicyFileContent content = validatedPath.map(path -> readPolicyFile(root, path)).orElse(null);
        if (content == null || content.content() == null) {
            return false;
        }
        boolean verifiedAresVersion = readAresVersion(root).filter(SecurityPolicySchema.VERIFIED_ARES_VERSION::equals).isPresent();
        Optional<String> updated = SecurityPolicyYaml.addTestClasses(content.content(), testClasses, verifiedAresVersion);
        updated.ifPresent(policy -> writePolicy(root, validatedPath.get(), policy));
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
        policy.append("  theFollowingProgrammingLanguageConfigurationIsUsed: ").append(buildTool.aresLanguageConfiguration()).append('\n');
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
        return TestRepositoryFiles.testClasses(summaries).stream().map(AresTestSources.TypeDeclaration::qualifiedName).sorted().toList();
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
     * Collects the Java sources of a checkout. The directories {@link TestRepositoryFiles#isJavaSourcePath} ignores anyway, above all {@code .git}, are not entered: Git and JGit
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
                    return !directory.equals(repositoryRoot) && TestRepositoryFiles.IGNORED_DIRECTORIES.contains(directory.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && TestRepositoryFiles.isJavaSourcePath(repositoryRoot.relativize(file).toString().replace('\\', '/'), isFile)) {
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
        return repositoryRoot.resolve(buildTool == BuildTool.GRADLE ? TestRepositoryFiles.GRADLE_BUILD_FILE : TestRepositoryFiles.MAVEN_BUILD_FILE);
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
