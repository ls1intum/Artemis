package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
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
import org.apache.maven.shared.invoker.*;
import org.apache.maven.shared.utils.Os;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.LocalRepository;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExerciseTemplateIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTemplateIntegrationTest.class);

    private static final String TEST_PREFIX = "progextemplate";

    private static File java17Home;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @Autowired
    private CourseUtilService courseUtilService;

    private ProgrammingExercise exercise;

    private final LocalRepository exerciseRepo = new LocalRepository(defaultBranch);

    private final LocalRepository testRepo = new LocalRepository(defaultBranch);

    private final LocalRepository solutionRepo = new LocalRepository(defaultBranch);

    private final LocalRepository auxRepo = new LocalRepository(defaultBranch);

    private static final String MAVEN_TEST_RESULTS_PATH = "target/surefire-reports";

    private static final String GRADLE_TEST_RESULTS_PATH = "build/test-results/test";

    @BeforeAll
    static void detectMavenHome() {
        /*
         * Maven invoker only looks for those two values and ignores maven, even if it is available over PATH. Because Maven reports the path when "-version" is used, we use that
         * to auto-detect the maven home and store it in the system properties.
         */
        String m2Home = System.getenv("M2_HOME");
        String mavenHome = System.getProperty("maven.home");

        if (m2Home != null || mavenHome != null) {
            return;
        }

        try {
            String mvnExecutable = Os.isFamily(Os.FAMILY_WINDOWS) ? "mvn.cmd" : "mvn";
            var lines = runProcess(new ProcessBuilder(mvnExecutable, "-version"));
            String prefix = "maven home:";
            Optional<String> home = lines.stream().filter(line -> line.toLowerCase().startsWith(prefix)).findFirst();
            if (home.isPresent()) {
                System.setProperty("maven.home", home.get().substring(prefix.length()).strip());
            }
            else {
                fail("maven home not found, unexpected '-version' format");
            }
        }
        catch (Exception e) {
            fail("maven home not found", e);
        }
    }

    @BeforeAll
    static void findAndSetJava17Home() throws Exception {
        if (Os.isFamily(Os.FAMILY_UNIX) || Os.isFamily(Os.FAMILY_MAC)) {
            // Use which to find all java installations on Linux
            var javaInstallations = runProcess(new ProcessBuilder("which", "-a", "java"));
            for (String path : javaInstallations) {
                File binFolder = new File(path).getParentFile();
                if (checkJavaVersion(binFolder, "./java", "-version")) {
                    return;
                }
            }

            var alternativeInstallations = runProcess(new ProcessBuilder("/usr/libexec/java_home", "-v", "17"));
            for (String path : alternativeInstallations) {
                File binFolder = new File(path).getParentFile();
                binFolder = new File(binFolder, "Home/bin");
                if (checkJavaVersion(binFolder, "./java", "-version")) {
                    return;
                }
            }
        }
        else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            // Use PATH to find all java installations on windows
            String[] path = System.getenv("PATH").split(";");
            var java17 = Arrays.stream(path).map(Path::of).filter(p -> p.endsWith("bin")).filter(Files::isDirectory).filter(binDir -> Files.exists(binDir.resolve("java.exe")))
                    .filter(binDir -> {
                        try {
                            return checkJavaVersion(binDir.toFile(), "cmd", "/c", "java.exe", "-version");
                        }
                        catch (Exception e) {
                            return false;
                        }
                    }).findFirst();

            if (java17.isPresent()) {
                return;
            }
        }
        fail("Java 17 not found");
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
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        auxRepo.configureRepos("auxLocalRepo", "auxOriginRepo");

        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        programmingExerciseTestService.setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        programmingExerciseTestService.tearDown();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        auxRepo.resetLocalRepo();
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
        List<ProgrammingLanguage> programmingLanguages = List.of(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN);
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

            if (languageFeatures.testwiseCoverageAnalysisSupported()) {
                if (projectTypes.isEmpty()) {
                    argumentBuilder.add(Arguments.of(language, null, true));
                }
                for (ProjectType projectType : projectTypes) {
                    if (projectType == ProjectType.MAVEN_BLACKBOX) {
                        continue;
                    }
                    argumentBuilder.add(Arguments.of(language, projectType, true));
                }
            }
        }
        return argumentBuilder.build();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void testTemplateExercise(ProgrammingLanguage language, ProjectType projectType, boolean testwiseCoverageAnalysis) throws Exception {
        runTests(language, projectType, exerciseRepo, TestResult.FAILED, testwiseCoverageAnalysis);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void testTemplateSolution(ProgrammingLanguage language, ProjectType projectType, boolean testwiseCoverageAnalysis) throws Exception {
        runTests(language, projectType, solutionRepo, TestResult.SUCCESSFUL, testwiseCoverageAnalysis);
    }

    private void runTests(ProgrammingLanguage language, ProjectType projectType, LocalRepository repository, TestResult testResult, boolean testwiseCoverageAnalysis)
            throws Exception {
        exercise.setProgrammingLanguage(language);
        exercise.setProjectType(projectType);
        mockConnectorRequestsForSetup(exercise, false, true, false);
        exercise.setChannelName("exercise-pe");
        if (testwiseCoverageAnalysis) {
            exercise.setTestwiseCoverageEnabled(true);
        }
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(repository);
        int exitCode;
        if (projectType != null && projectType.isGradle()) {
            exitCode = invokeGradle(testwiseCoverageAnalysis);
        }
        else {
            exitCode = invokeMaven(testwiseCoverageAnalysis);
        }

        if (TestResult.SUCCESSFUL.equals(testResult)) {
            assertThat(exitCode).isZero();
        }
        else {
            assertThat(exitCode).isNotZero();
        }

        var testReportPath = projectType != null && projectType.isGradle() ? GRADLE_TEST_RESULTS_PATH : MAVEN_TEST_RESULTS_PATH;
        var testResults = readTestReports(testReportPath);
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(testResult, 12 + (ProgrammingLanguage.JAVA.equals(language) ? 1 : 0)));
    }

    private int invokeMaven(boolean testwiseCoverageAnalysis) throws MavenInvocationException {
        InvocationRequest mvnRequest = new DefaultInvocationRequest();
        mvnRequest.setJavaHome(java17Home);
        mvnRequest.setPomFile(testRepo.localRepoFile);
        mvnRequest.setGoals(List.of("clean", "test"));
        if (testwiseCoverageAnalysis) {
            mvnRequest.addArg("-Pcoverage");
        }
        mvnRequest.setShowVersion(true);

        Invoker mvnInvoker = new DefaultInvoker();
        InvocationResult result = mvnInvoker.execute(mvnRequest);

        assertThat(result.getExecutionException()).isNull();
        return result.getExitCode();
    }

    private int invokeGradle(boolean recordTestwiseCoverage) {
        try (ProjectConnection connector = GradleConnector.newConnector().forProjectDirectory(testRepo.localRepoFile).useBuildDistribution().connect()) {
            BuildLauncher launcher = connector.newBuild();
            launcher.setJavaHome(java17Home);
            String[] tasks;
            if (recordTestwiseCoverage) {
                tasks = new String[] { "clean", "test", "tiaTests", "--run-all-tests" };
            }
            else {
                tasks = new String[] { "clean", "test" };
            }
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

    private void moveAssignmentSourcesOf(LocalRepository localRepository) throws IOException {
        Path sourceSrc = localRepository.localRepoFile.toPath().resolve("src");
        Path assignment = testRepo.localRepoFile.toPath().resolve("assignment");
        Files.createDirectories(assignment);
        FileUtils.moveDirectory(sourceSrc.toFile(), assignment.resolve("src").toFile());
    }

    private Map<TestResult, Integer> readTestReports(String testResultPath) {
        File reportFolder = testRepo.localRepoFile.toPath().resolve(testResultPath).toFile();
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
