package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExerciseTemplateIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

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

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 1, 1, 0, 1);
        doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
        Course course = courseUtilService.addEmptyCourse();
        exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();
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

    private void runTests(ProgrammingLanguage language, ProjectType projectType, RepositoryType repositoryType, TestResult testResult) throws Exception {
        exercise.setProgrammingLanguage(language);
        exercise.setProjectType(projectType);
        mockConnectorRequestsForSetup(exercise, false, true, false);
        exercise.setChannelName("exercise-pe");
        exercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", exercise, ProgrammingExercise.class, HttpStatus.CREATED);
        LocalVCRepositoryUri assignmentUri = exercise.getRepositoryURI(repositoryType);
        LocalVCRepositoryUri testUri = exercise.getRepositoryURI(RepositoryType.TESTS);
        Repository assignmentRepository = gitService.getOrCheckoutRepository(assignmentUri, true, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testUri, true, true);
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
        var testResults = readTestReports(testRepository.getLocalPath(), testReportPath);
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(testResult, 12 + (ProgrammingLanguage.JAVA.equals(language) ? 1 : 0)));
    }

    private int invokeMaven(Path testRepositoryPath) throws MavenInvocationException {
        InvocationRequest mvnRequest = new DefaultInvocationRequest();
        mvnRequest.setJavaHome(java17Home);
        mvnRequest.setPomFile(testRepositoryPath.toFile());
        mvnRequest.addArgs(List.of("clean", "test"));
        mvnRequest.setShowVersion(true);

        Invoker mvnInvoker = new DefaultInvoker();
        InvocationResult result = mvnInvoker.execute(mvnRequest);

        assertThat(result.getExecutionException()).isNull();
        return result.getExitCode();
    }

    private int invokeGradle(Path testRepositoryPath) {
        try (ProjectConnection connector = GradleConnector.newConnector().forProjectDirectory(testRepositoryPath.toFile()).useBuildDistribution().connect()) {
            BuildLauncher launcher = connector.newBuild();
            launcher.setJavaHome(java17Home);
            String[] tasks = new String[] { "clean", "test" };
            launcher.forTasks(tasks);
            launcher.run();
        }
        catch (Exception e) {
            // printing the cause because this contains the relevant error message (and not a generic one from the connector)
            log.error("Error occurred while executing Gradle build.", e.getCause());
            return -1;
        }
        return 0;
    }

    private void moveAssignmentSourcesOf(Path assignmentRepositoryPath, Path testRepositoryPath) throws IOException {
        Path sourceSrc = assignmentRepositoryPath.resolve("src");
        Path assignment = testRepositoryPath.resolve("assignment");
        Files.createDirectories(assignment);
        FileUtils.moveDirectory(sourceSrc.toFile(), assignment.resolve("src").toFile());
    }

    private Map<TestResult, Integer> readTestReports(Path testRepositoryPath, String testResultPath) {
        File reportFolder = testRepositoryPath.resolve(testResultPath).toFile();
        assertThat(reportFolder).as("test reports generated").matches(SurefireReportParser::hasReportFiles, "the report folder should contain test reports");

        // Note that the locale does not have any effect on parsing and is only used in some other methods
        SurefireReportParser reportParser = new SurefireReportParser(List.of(reportFolder), new PrintStreamLogger(System.out));
        List<ReportTestSuite> reports = reportParser.parseXMLReportFiles();
        return reports.stream().flatMap(testSuite -> testSuite.getTestCases().stream()).map(TestResult::of)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt(testCase -> 1)));
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
