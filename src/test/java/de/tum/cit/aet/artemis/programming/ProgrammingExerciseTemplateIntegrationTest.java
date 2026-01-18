package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.utils.Os;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

// ExecutionMode.SAME_THREAD ensures that all tests within this class are executed sequentially in the same thread, rather than in parallel or in a different thread.
// This is important in the context of Maven because it avoids potential race conditions or inconsistencies that could arise if multiple test methods are executed simultaneously.
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExerciseTemplateIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTemplateTest {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTemplateIntegrationTest.class);

    private static final String TEST_PREFIX = "progextemplate";

    private static File java17Home;

    private ProgrammingExercise exercise;

    private static final String MAVEN_TEST_RESULTS_PATH = "target/surefire-reports";

    private static final String GRADLE_TEST_RESULTS_PATH = "build/test-results/test";

    @BeforeAll
    static void detectMavenHome() {
        /*
         * Maven invoker only looks for system properties and ignores maven, even if it is available over PATH.
         * Because Maven reports the path when "-version" is used, we use that to auto-detect the maven home and store it in the system properties.
         */

        if (isMavenHomeSet()) {
            return;
        }

        try {
            String mvnExecutable = Os.isFamily(Os.FAMILY_WINDOWS) ? "mvn.cmd" : "mvn";
            var lines = runProcess(new ProcessBuilder(mvnExecutable, "-version"));
            String prefix = "maven home:";
            Optional<String> home = lines.stream().filter(line -> line.toLowerCase().startsWith(prefix)).findFirst();
            home.ifPresent(homeLocation -> System.setProperty("maven.home", homeLocation.substring(prefix.length()).strip()));
        }
        catch (Exception e) {
            log.debug("maven home not found", e);
        }
    }

    private static boolean isMavenHomeSet() {
        String m2Home = System.getenv("M2_HOME");
        String mavenHome = System.getProperty("maven.home");

        return m2Home != null || mavenHome != null;
    }

    @BeforeAll
    static void findAndSetJava17Home() throws Exception {
        if (Os.isFamily(Os.FAMILY_UNIX) || Os.isFamily(Os.FAMILY_MAC)) {
            findAndSetJava17UnixSystems();
        }
        else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            findAndSetJava17Windows();
        }
    }

    private static void findAndSetJava17UnixSystems() throws Exception {
        // Use which to find all java installations on Linux
        var javaInstallations = runProcess(new ProcessBuilder("which", "-a", "java"));
        for (String path : javaInstallations) {
            File binFolder = Path.of(path).toFile().getParentFile();
            if (checkJavaVersion(binFolder, "./java", "-version")) {
                return;
            }
        }

        // Mac systems have additional locations where Java could potentially be
        if (Os.isFamily(Os.FAMILY_MAC)) {
            findAndSetJava17Mac();
        }
    }

    private static void findAndSetJava17Mac() throws Exception {
        var alternativeInstallations = runProcess(new ProcessBuilder("/usr/libexec/java_home", "-v", "17"));
        for (String path : alternativeInstallations) {
            File binFolder = Path.of(path).toFile().getParentFile();
            binFolder = binFolder.toPath().resolve("Home/bin").toFile();
            if (checkJavaVersion(binFolder, "./java", "-version")) {
                return;
            }
        }
    }

    private static void findAndSetJava17Windows() {
        // Use PATH to find all java installations on windows
        String[] path = System.getenv("PATH").split(";");
        Arrays.stream(path).map(Path::of).filter(p -> p.endsWith("bin")).filter(Files::isDirectory).filter(binDir -> Files.exists(binDir.resolve("java.exe"))).forEach(binDir -> {
            try {
                checkJavaVersion(binDir.toFile(), "cmd", "/c", "java.exe", "-version");
            }
            catch (Exception e) {
                // ignore: we still continue to find another Java installation
            }
        });
    }

    private static boolean checkJavaVersion(File binFolder, String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command).directory(binFolder);
        var version = runProcess(processBuilder);
        if (!version.isEmpty() && version.getFirst().contains("version \"17")) {
            java17Home = binFolder.getParentFile(); // JAVA_HOME/bin/java
            log.debug("Using {} as JAVA_HOME.", java17Home);
            return true;
        }
        return false;
    }

    private static List<String> runProcess(ProcessBuilder processBuilder) throws Exception {
        Process process = processBuilder.start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);

        if (!finished) {
            throw new TimeoutException("command timed out.");
        }
        try (BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return Stream.concat(error.lines(), stdout.lines()).toList();
        }
    }

    /**
     * Cleans up local repositories associated with the current exercise to prevent state leaking between tests.
     */
    private void cleanUpRepositories() {
        if (exercise != null && exercise.getId() != null) {
            List<RepositoryType> repositoryTypes = List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);
            List<Exception> cleanupFailures = new java.util.ArrayList<>();

            for (RepositoryType type : repositoryTypes) {
                try {
                    LocalVCRepositoryUri uri = exercise.getRepositoryURI(type);

                    // Get the local repository path before deletion to verify cleanup
                    Path repoPath = uri.getLocalRepositoryPath(localVCBasePath);

                    gitServiceSpy.deleteLocalRepository(uri);

                    deleteDirectoryWithRetries(repoPath, type.toString());
                }
                catch (Exception e) {
                    log.error("Failed to clean up {} repository", type, e);
                    cleanupFailures.add(e);
                }
            }

            // If cleanup failed, it might cause subsequent tests to fail
            if (!cleanupFailures.isEmpty()) {
                log.warn("Cleanup incomplete. {} repositories failed to clean up. This may cause subsequent test failures.", cleanupFailures.size());
            }
        }
    }

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course = courseUtilService.addEmptyCourse();
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
        this.cleanUpRepositories();
    }

    /**
     * Verifies that a repository has been properly checked out and initialized.
     * Checks that the repository path exists and contains expected files.
     *
     * @param repository  the repository to verify
     * @param description description for logging
     * @throws IllegalStateException if repository is not properly set up
     */
    private void verifyRepositorySetup(Repository repository, String description) {
        Path repoPath = repository.getLocalPath();

        if (!Files.exists(repoPath)) {
            throw new IllegalStateException(String.format("%s path does not exist: %s", description, repoPath));
        }

        if (!Files.isDirectory(repoPath)) {
            throw new IllegalStateException(String.format("%s path is not a directory: %s", description, repoPath));
        }

        // Check that the repository contains some files (not empty)
        try (Stream<Path> files = Files.list(repoPath)) {
            long fileCount = files.count();
            if (fileCount == 0) {
                throw new IllegalStateException(String.format("%s directory is empty: %s", description, repoPath));
            }
            log.debug("Verified {} has {} items at: {}", description, fileCount, repoPath);
        }
        catch (IOException e) {
            throw new IllegalStateException(String.format("Failed to verify %s at: %s", description, repoPath), e);
        }
    }

    /**
     * Waits for a specific file to exist in a directory with retry logic.
     * This is crucial for slow CI environments where git operations may not complete immediately.
     *
     * @param directory   the directory containing the file
     * @param fileName    the name of the file to wait for
     * @param description description for logging
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if file does not exist after all retries
     */
    private void waitForFileToExist(Path directory, String fileName, String description) throws InterruptedException {
        Path filePath = directory.resolve(fileName);
        final int maxRetries = 20;
        final int retryDelayMs = 500;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (Files.exists(filePath)) {
                log.info("{} ({}) found after {} attempt(s) at: {}", description, fileName, attempt, filePath);
                return;
            }

            if (attempt < maxRetries) {
                log.debug("{} ({}) not found yet (attempt {}/{}), waiting {} ms...", description, fileName, attempt, maxRetries, retryDelayMs);
                Thread.sleep(retryDelayMs);
            }
        }

        // Log diagnostic information for debugging
        String diagnosticInfo = String.format("Directory %s exists: %s", directory, Files.exists(directory));
        if (Files.exists(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                List<String> fileNames = files.map(Path::getFileName).map(Path::toString).toList();
                diagnosticInfo += String.format(", contains %d files: %s", fileNames.size(), fileNames);
            }
            catch (IOException e) {
                diagnosticInfo += ", unable to list files: " + e.getMessage();
            }
        }

        log.error("{} ({}) not found after {} retries. {}", description, fileName, maxRetries, diagnosticInfo);
        throw new IllegalStateException(String.format("%s (%s) not found in directory %s after %d attempts. %s", description, fileName, directory, maxRetries, diagnosticInfo));
    }

    /**
     * Waits for a specific directory to exist with retry logic.
     * This is crucial for slow CI environments where repository operations may not complete immediately.
     *
     * @param directoryPath the path of the directory to wait for
     * @param description   description for logging
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if directory does not exist after all retries
     */
    private void waitForDirectoryToExist(Path directoryPath, String description) throws InterruptedException {
        final int maxRetries = 30; // Increased retries for slow CI
        final int retryDelayMs = 1000; // 1 second delay (30 seconds total)

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (Files.exists(directoryPath) && Files.isDirectory(directoryPath)) {
                log.info("{} found after {} attempt(s) at: {}", description, attempt, directoryPath);
                return;
            }

            if (attempt < maxRetries) {
                log.debug("{} not found yet (attempt {}/{}), waiting {} ms...", description, attempt, maxRetries, retryDelayMs);
                Thread.sleep(retryDelayMs);
            }
        }

        // Log diagnostic information for debugging
        Path parentDir = directoryPath.getParent();
        String diagnosticInfo = String.format("Parent directory %s exists: %s", parentDir, Files.exists(parentDir));
        if (Files.exists(parentDir)) {
            try (Stream<Path> files = Files.list(parentDir)) {
                List<String> dirNames = files.filter(Files::isDirectory).map(Path::getFileName).map(Path::toString).toList();
                diagnosticInfo += String.format(", contains %d subdirectories: %s", dirNames.size(), dirNames);
            }
            catch (IOException e) {
                diagnosticInfo += ", unable to list parent directory: " + e.getMessage();
            }
        }

        log.error("{} not found after {} retries. {}", description, maxRetries, diagnosticInfo);
        throw new IllegalStateException(String.format("%s not found at %s after %d attempts. %s", description, directoryPath, maxRetries, diagnosticInfo));
    }

    /**
     * Waits for repositories to be initialized on disk after exercise creation.
     * Actively checks that repository paths exist rather than using a fixed delay.
     * This is critical in slow CI environments where background repository initialization may still be running.
     *
     * @param assignmentUri the assignment repository URI
     * @param testUri       the test repository URI
     * @throws InterruptedException  if interrupted while waiting
     * @throws IllegalStateException if repositories are not initialized after all retries
     */
    private void waitForRepositoriesToBeInitialized(LocalVCRepositoryUri assignmentUri, LocalVCRepositoryUri testUri) throws InterruptedException {
        int maxRetries = 15;
        int retryDelayMs = 500; // 500ms delay (7.5 seconds total)

        Path assignmentPath = assignmentUri.getLocalRepositoryPath(localVCBasePath);
        Path testPath = testUri.getLocalRepositoryPath(localVCBasePath);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            boolean assignmentExists = Files.exists(assignmentPath) && Files.isDirectory(assignmentPath);
            boolean testExists = Files.exists(testPath) && Files.isDirectory(testPath);

            if (assignmentExists && testExists) {
                log.info("Repositories initialized after {} attempt(s)", attempt);
                return;
            }

            if (attempt < maxRetries) {
                log.debug("Repositories not yet initialized (attempt {}/{}): assignment={}, test={}", attempt, maxRetries, assignmentExists, testExists);
                Thread.sleep(retryDelayMs);
            }
        }

        String diagnosticInfo = String.format("Assignment repository exists: %s, Test repository exists: %s", Files.exists(assignmentPath), Files.exists(testPath));
        log.error("Repositories not initialized after {} retries. {}", maxRetries, diagnosticInfo);
        throw new IllegalStateException(String.format("Repositories not initialized after %d attempts. %s", maxRetries, diagnosticInfo));
    }

    /**
     * Robust directory deletion with retries to handle OS file locks.
     * Repeatedly attempts to delete the directory until successful or max retries reached.
     *
     * @param directory   the directory to delete
     * @param description description for logging (e.g., repository type)
     * @throws IOException if deletion fails after all retries
     */
    private void deleteDirectoryWithRetries(Path directory, String description) throws IOException {
        if (!Files.exists(directory)) {
            log.debug("{} directory does not exist, no cleanup needed: {}", description, directory);
            return;
        }

        int maxAttempts = 10;
        int attemptDelayMs = 100;
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                FileUtils.deleteDirectory(directory.toFile());

                boolean deletionWasSuccessful = !Files.exists(directory);
                if (deletionWasSuccessful) {
                    log.debug("Successfully deleted {} directory after {} attempt(s): {}", description, attempt, directory);
                    return;
                }
            }
            catch (IOException e) {
                lastException = e;
                log.debug("{} directory deletion attempt {}/{} failed: {}", description, attempt, maxAttempts, e.getMessage());
            }

            // Wait before retry to allow OS to release file locks
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(attemptDelayMs);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying directory deletion", e);
                }
            }
        }

        boolean allDeletionAttemptsFailed = Files.exists(directory);
        if (allDeletionAttemptsFailed) {
            String message = String.format("Failed to delete %s directory after %d attempts: %s", description, maxAttempts, directory);
            log.error(message);
            if (lastException != null) {
                throw new IOException(message, lastException);
            }
            else {
                throw new IOException(message);
            }
        }
    }

    /**
     * Build a combination of valid programming languages and project types.
     * Programming languages without project type only have one template, set null to use this one.
     * Programming languages with project type should be executed once per project type.
     *
     * @return valid combinations of programming languages and project types.
     */
    private Stream<Arguments> languageTypeBuilder() {
        Stream.Builder<Arguments> argumentBuilder = Stream.builder();
        // Add programming exercises that should be tested with Maven or Gradle here
        List<ProgrammingLanguage> programmingLanguages = List.of(ProgrammingLanguage.JAVA);
        for (ProgrammingLanguage language : programmingLanguages) {
            var languageFeatures = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language);
            var projectTypes = languageFeatures.projectTypes();
            if (projectTypes.isEmpty()) {
                argumentBuilder.add(Arguments.of(language, null, false));
            }
            for (ProjectType projectType : projectTypes) {
                // TODO: MAVEN_BLACKBOX Templates should be tested in the future!
                if (projectType == ProjectType.MAVEN_BLACKBOX) {
                    continue;
                }
                argumentBuilder.add(Arguments.of(language, projectType, false));
            }
        }
        return argumentBuilder.build();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void testTemplateExercise(ProgrammingLanguage language, ProjectType projectType) throws Exception {
        checkPreconditionsForJavaTemplateExecution(projectType);
        runTests(language, projectType, RepositoryType.TEMPLATE, TestResult.FAILED);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void testTemplateSolution(ProgrammingLanguage language, ProjectType projectType) throws Exception {
        checkPreconditionsForJavaTemplateExecution(projectType);
        runTests(language, projectType, RepositoryType.SOLUTION, TestResult.SUCCESSFUL);
    }

    private void checkPreconditionsForJavaTemplateExecution(final ProjectType projectType) {
        if (projectType == null || projectType.isMaven()) {
            assumeTrue(isMavenHomeSet(), "Could not find Maven. Skipping execution of template tests.");
        }
        assumeTrue(java17Home != null, "Could not find Java 17. Skipping execution of template tests.");
    }

    /**
     * Creates a programming exercise with retry logic to handle transient 500 errors.
     * In slow CI environments, exercise setup may fail transiently due to timing issues with LocalVC or Jenkins setup.
     *
     * @return the created programming exercise
     * @throws Exception if exercise creation fails after all retries
     */
    private ProgrammingExercise createExerciseWithRetry() throws Exception {
        int maxAttempts = 5;
        int retryDelayMs = 2000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ProgrammingExercise createdExercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", exercise, ProgrammingExercise.class,
                        HttpStatus.CREATED);
                log.info("Successfully created exercise on attempt {}/{}", attempt, maxAttempts);
                return createdExercise;
            }
            catch (Exception e) {
                lastException = e;
                log.warn("Exercise creation attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    log.debug("Waiting {} ms before retry...", retryDelayMs);
                    Thread.sleep(retryDelayMs);
                }
            }
        }

        log.error("Failed to create exercise after {} attempts", maxAttempts);
        throw new IllegalStateException("Exercise creation failed after " + maxAttempts + " attempts", lastException);
    }

    /**
     * Checks out a repository with retry logic to handle timing issues in slow CI environments.
     * Repository checkout may fail if the repository is not fully initialized on disk yet.
     * <p>
     * Git repository initialization is not atomic: directories, objects, and refs are written
     * sequentially. In CI environments with slower I/O or high concurrency, the repository
     * creation may return before all files are fully flushed to disk. Waiting allows pending
     * file system operations to complete, making subsequent checkout attempts succeed.
     *
     * @param repositoryUri the URI of the repository to checkout
     * @param description   description for logging
     * @return the checked out repository
     * @throws Exception if checkout fails after all retries
     */
    private Repository checkoutRepositoryWithRetry(LocalVCRepositoryUri repositoryUri, String description) throws Exception {
        int maxAttempts = 10;
        int retryDelayMs = 500;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Repository repository = gitServiceSpy.getOrCheckoutRepository(repositoryUri, true, true);
                verifyRepositorySetup(repository, description);
                log.info("Successfully checked out {} on attempt {}/{}", description, attempt, maxAttempts);
                return repository;
            }
            catch (Exception e) {
                lastException = e;
                log.debug("{} checkout attempt {}/{} failed: {}", description, attempt, maxAttempts, e.getMessage());

                if (attempt < maxAttempts) {
                    log.debug("Waiting {} ms before retry...", retryDelayMs);
                    Thread.sleep(retryDelayMs);
                }
            }
        }

        log.error("Failed to checkout {} after {} attempts", description, maxAttempts);
        throw new IllegalStateException(description + " checkout failed after " + maxAttempts + " attempts", lastException);
    }

    private void runTests(ProgrammingLanguage language, ProjectType projectType, RepositoryType repositoryType, TestResult testResult) throws Exception {
        // Add unique identifier to prevent repository path collisions between parameterized test iterations
        // This is critical because multiple tests may run in sequence and need isolated file system state
        // Use UUID to ensure absolute uniqueness (take first 8 chars to stay within shortname length limits)
        String uniqueId = UUID.randomUUID().toString().substring(0, 8).replace("-", "");
        String originalShortName = exercise.getShortName();
        exercise.setShortName(originalShortName + uniqueId);
        log.debug("Running test with unique exercise short name: {}", exercise.getShortName());

        exercise.setProgrammingLanguage(language);
        exercise.setProjectType(projectType);
        mockConnectorRequestsForSetup(exercise, false, true, false);
        exercise.setChannelName("exercise-pe");

        exercise = createExerciseWithRetry();

        LocalVCRepositoryUri assignmentUri = exercise.getRepositoryURI(repositoryType);
        LocalVCRepositoryUri testUri = exercise.getRepositoryURI(RepositoryType.TESTS);

        waitForRepositoriesToBeInitialized(assignmentUri, testUri);
        Repository assignmentRepository = null;
        Repository testRepository = null;

        try {
            // Use retry logic for repository checkout to handle timing issues in slow CI environments
            assignmentRepository = checkoutRepositoryWithRetry(assignmentUri, "assignment repository (" + repositoryType + ")");
            testRepository = checkoutRepositoryWithRetry(testUri, "test repository");

            // Wait for specific build files to exist in test repository with retry logic
            // This addresses the "pom.xml not found" errors in slow CI environments
            String buildFileName = (projectType != null && projectType.isGradle()) ? "build.gradle" : "pom.xml";
            waitForFileToExist(testRepository.getLocalPath(), buildFileName, "test repository build file");

            // Wait for src directory to exist in assignment repository
            // This is critical because the repository might be checked out but not yet populated
            waitForDirectoryToExist(assignmentRepository.getLocalPath().resolve("src"), "assignment repository src directory");

            moveAssignmentSourcesOf(assignmentRepository.getLocalPath(), testRepository.getLocalPath());
            int exitCode;
            if (projectType != null && projectType.isGradle()) {
                exitCode = invokeGradle(testRepository.getLocalPath());
            }
            else {
                exitCode = invokeMaven(testRepository.getLocalPath());
            }

            if (TestResult.SUCCESSFUL.equals(testResult)) {
                assertThat(exitCode).isZero();
            }
            else {
                assertThat(exitCode).isNotZero();
            }

            var testReportPath = projectType != null && projectType.isGradle() ? GRADLE_TEST_RESULTS_PATH : MAVEN_TEST_RESULTS_PATH;
            var testResults = readTestReportsWithRetries(testRepository.getLocalPath(), testReportPath);
            assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(testResult, 12 + (ProgrammingLanguage.JAVA.equals(language) ? 1 : 0)));
        }
        finally {
            // Ensure repositories are properly closed before cleanup to release file locks
            // This is critical for preventing "No such file or directory" errors in cleanup
            if (assignmentRepository != null) {
                assignmentRepository.close();
            }
            if (testRepository != null) {
                testRepository.close();
            }
        }
    }

    private int invokeMaven(Path testRepositoryPath) throws MavenInvocationException, IOException {
        log.info("Invoking Maven in directory: {}", testRepositoryPath);

        // Validate that pom.xml exists
        Path pomFile = testRepositoryPath.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            log.error("pom.xml not found at: {}", pomFile);
            log.error("Directory contents: {}", listDirectoryContents(testRepositoryPath));
            throw new IllegalStateException("pom.xml not found in test repository: " + testRepositoryPath);
        }
        log.info("Found pom.xml at: {}", pomFile);

        // Validate that assignment/src exists (the moved source files)
        Path assignmentSrc = testRepositoryPath.resolve("assignment/src");
        if (!Files.exists(assignmentSrc)) {
            log.error("assignment/src not found at: {}", assignmentSrc);
            log.error("Directory contents: {}", listDirectoryContents(testRepositoryPath));
            throw new IllegalStateException("assignment/src not found in test repository: " + testRepositoryPath);
        }
        log.info("Found assignment/src at: {}", assignmentSrc);

        // Use a fresh local Maven repository to avoid cache interference between tests
        Path localMavenRepo = testRepositoryPath.resolve(".m2-test-repo");
        Files.createDirectories(localMavenRepo);
        log.info("Using isolated Maven local repository: {}", localMavenRepo);

        InvocationRequest mvnRequest = new DefaultInvocationRequest();
        mvnRequest.setJavaHome(java17Home);
        mvnRequest.setPomFile(testRepositoryPath.toFile());
        mvnRequest.addArgs(List.of("clean", "test", "-Dmaven.repo.local=" + localMavenRepo.toAbsolutePath(), "-B"));
        mvnRequest.setShowVersion(true);
        mvnRequest.setBatchMode(true);

        // Capture Maven output for debugging
        StringBuilder mavenOutput = new StringBuilder();
        mvnRequest.setOutputHandler(line -> {
            mavenOutput.append(line).append("\n");
            log.debug("[Maven] {}", line);
        });
        mvnRequest.setErrorHandler(line -> {
            mavenOutput.append("[ERROR] ").append(line).append("\n");
            log.warn("[Maven ERROR] {}", line);
        });

        Invoker mvnInvoker = new DefaultInvoker();
        InvocationResult result = mvnInvoker.execute(mvnRequest);

        log.info("Maven execution completed with exit code: {}", result.getExitCode());
        if (result.getExecutionException() != null) {
            log.error("Maven execution exception: ", result.getExecutionException());
        }

        // Log summary of Maven output on failure or unexpected success
        if (result.getExitCode() == 0) {
            log.info("Maven build succeeded. Output summary (last 50 lines):\n{}", getLastLines(mavenOutput.toString(), 50));
        }
        else {
            log.info("Maven build failed as expected. Output summary (last 50 lines):\n{}", getLastLines(mavenOutput.toString(), 50));
        }

        assertThat(result.getExecutionException()).isNull();
        return result.getExitCode();
    }

    private String listDirectoryContents(Path directory) {
        try (Stream<Path> paths = Files.walk(directory, 2)) {
            return paths.map(p -> directory.relativize(p).toString()).collect(Collectors.joining(", "));
        }
        catch (IOException e) {
            return "Unable to list directory: " + e.getMessage();
        }
    }

    private String getLastLines(String text, int lineCount) {
        String[] lines = text.split("\n");
        int start = Math.max(0, lines.length - lineCount);
        return String.join("\n", Arrays.copyOfRange(lines, start, lines.length));
    }

    private int invokeGradle(Path testRepositoryPath) {
        log.info("Invoking Gradle in directory: {}", testRepositoryPath);

        // Use ExecutorService to enforce a timeout on Gradle builds
        // This prevents indefinite hangs in slow CI environments
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = null;
        try {
            future = executor.submit(() -> {
                try (ProjectConnection connector = GradleConnector.newConnector().forProjectDirectory(testRepositoryPath.toFile()).useBuildDistribution().connect()) {
                    BuildLauncher launcher = connector.newBuild();
                    launcher.setJavaHome(java17Home);
                    String[] tasks = new String[] { "clean", "test" };
                    launcher.forTasks(tasks);
                    launcher.run();
                    log.info("Gradle build completed successfully");
                    return 0;
                }
                catch (Exception e) {
                    // printing the cause because this contains the relevant error message (and not a generic one from the connector)
                    log.error("Error occurred while executing Gradle build.", e.getCause());
                    return -1;
                }
            });

            // Wait up to 5 minutes for Gradle build to complete
            // This is generous but necessary for slow CI environments with dependency downloads
            return future.get(5, TimeUnit.MINUTES);
        }
        catch (TimeoutException e) {
            log.error("Gradle build timed out after 5 minutes in directory: {}", testRepositoryPath);
            if (future != null) {
                future.cancel(true);
            }
            return -1;
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Gradle build was interrupted or failed with exception", e);
            Thread.currentThread().interrupt();
            return -1;
        }
        finally {
            executor.shutdownNow();
        }
    }

    /**
     * Forces synchronization of all files in a directory to disk.
     * This prevents race conditions where Gradle/Maven start before files are ready,
     * especially on network-mounted file systems.
     */
    private void syncFilesToDisk(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                    channel.force(true);
                }
                catch (IOException e) {
                    log.warn("Could not force disk sync for file: {} - {}", file, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        log.debug("File system synchronization completed for: {}", directory);
    }

    private void moveAssignmentSourcesOf(Path assignmentRepositoryPath, Path testRepositoryPath) throws IOException {
        Path sourceSrc = assignmentRepositoryPath.resolve("src");
        Path assignment = testRepositoryPath.resolve("assignment");
        Files.createDirectories(assignment);
        FileUtils.moveDirectory(sourceSrc.toFile(), assignment.resolve("src").toFile());

        syncFilesToDisk(assignment.resolve("src"));
    }

    private Map<TestResult, Integer> readTestReportsWithRetries(Path testRepositoryPath, String testResultPath) throws InterruptedException {
        File reportFolder = testRepositoryPath.resolve(testResultPath).toFile();

        // Retry logic to handle timing issues where test reports might not be immediately available
        // Increased retries and delay to handle systems under load, especially in CI environments
        final int maxRetries = 30; // Increased from 10 for slow CI environments
        final int retryDelayMs = 2000; // Increased from 1000ms for slow CI environments (60s total)
        boolean hasReports = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (SurefireReportParser.hasReportFiles(reportFolder)) {
                hasReports = true;
                log.info("Test reports found after {} attempt(s) in {}", attempt, reportFolder);
                break;
            }
            if (attempt < maxRetries) {
                // Log diagnostic information about the report folder
                if (reportFolder.exists()) {
                    File[] files = reportFolder.listFiles();
                    if (files != null && files.length > 0) {
                        log.debug("Report folder exists with {} file(s) but no valid reports found yet: {}", files.length, Arrays.toString(files));
                    }
                    else {
                        log.debug("Report folder exists but is empty (attempt {}/{})", attempt, maxRetries);
                    }
                }
                else {
                    log.debug("Report folder does not exist yet: {} (attempt {}/{})", reportFolder, attempt, maxRetries);
                }

                log.debug("Waiting {} ms before next attempt...", retryDelayMs);
                Thread.sleep(retryDelayMs);
            }
        }

        // Provide detailed failure message if reports are not found
        if (!hasReports) {
            String diagnosticInfo = "Report folder status: ";
            if (reportFolder.exists()) {
                File[] files = reportFolder.listFiles();
                diagnosticInfo += String.format("exists with %d files: %s", files != null ? files.length : 0, files != null ? Arrays.toString(files) : "null");
            }
            else {
                diagnosticInfo += "does not exist";
            }
            log.error("Test reports not found after {} retries. {}", maxRetries, diagnosticInfo);
        }

        assertThat(hasReports).as("test reports generated after " + maxRetries + " retries")
                .withFailMessage("The report folder %s should contain test reports but none were found after %d attempts", reportFolder, maxRetries).isTrue();

        // Note that the locale does not have any effect on parsing and is only used in some other methods
        SurefireReportParser reportParser = new SurefireReportParser(List.of(reportFolder), new PrintStreamLogger(System.out));
        List<ReportTestSuite> reports = reportParser.parseXMLReportFiles();
        return reports.stream().flatMap(testSuite -> testSuite.getTestCases().stream()).map(TestResult::of)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(_ -> 1)));
    }

    private enum TestResult {

        SUCCESSFUL, FAILED, ERROR, SKIPPED;

        static TestResult of(ReportTestCase testCase) {
            if (testCase.hasError()) {
                return TestResult.ERROR;
            }
            if (testCase.hasFailure()) {
                return TestResult.FAILED;
            }
            if (testCase.hasSkipped()) {
                return TestResult.SKIPPED;
            }
            return TestResult.SUCCESSFUL;
        }
    }
}
