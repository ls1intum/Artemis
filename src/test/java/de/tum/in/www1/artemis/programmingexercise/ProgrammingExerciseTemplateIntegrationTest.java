package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.reset;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.invoker.*;
import org.apache.maven.shared.utils.Os;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import net.sourceforge.plantuml.Log;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgrammingExerciseTemplateIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private ProgrammingExercise exercise;

    private final LocalRepository exerciseRepo = new LocalRepository(defaultBranch);

    private final LocalRepository testRepo = new LocalRepository(defaultBranch);

    private final LocalRepository solutionRepo = new LocalRepository(defaultBranch);

    private final LocalRepository auxRepo = new LocalRepository(defaultBranch);

    private final static String MAVEN_TEST_RESULTS_PATH = "target/surefire-reports";

    private final static String GRADLE_TEST_RESULTS_PATH = "build/test-results/test";

    @BeforeAll
    static void detectMavenHome() {
        /*
         * Maven invoker only looks for those two values and ignores maven, even if it is available over PATH. Because Maven reports the path when "-version" is used, we use that
         * to auto-detect the maven home and store it in the system properties.
         */
        String m2Home = System.getenv("M2_HOME");
        String mavenHome = System.getProperty("maven.home");

        if (m2Home != null || mavenHome != null)
            return;

        try {
            String mvnExecutable = Os.isFamily(Os.FAMILY_WINDOWS) ? "mvn.cmd" : "mvn";
            Process mvn = Runtime.getRuntime().exec(mvnExecutable + " -version");
            mvn.waitFor();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(mvn.getInputStream()))) {
                String prefix = "maven home:";
                Optional<String> home = br.lines().filter(line -> line.toLowerCase().startsWith(prefix)).findFirst();
                if (home.isPresent()) {
                    System.setProperty("maven.home", home.get().substring(prefix.length()).strip());
                }
                else {
                    fail("maven home not found, unexpected '-version' format");
                }
            }
        }
        catch (Exception e) {
            fail("maven home not found", e);
        }
    }

    @BeforeEach
    @SuppressWarnings("resource")
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(1, 1, 0, 1);
        Course course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests(true);

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        auxRepo.configureRepos("auxLocalRepo", "auxOriginRepo");

        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        programmingExerciseTestService.setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo, auxRepo);
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        reset(bambooServer);
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        auxRepo.resetLocalRepo();
    }

    /**
     * Build a combination of valid programming languages and project types.
     * Programming languages without project type only have one template, set null to use this one.
     * Programming languages with project type should be executed once per project type.
     * @return valid combinations of programming languages and project types.
     */
    private Stream<Arguments> languageTypeBuilder() {
        Stream.Builder<Arguments> argumentBuilder = Stream.builder();
        // Add programming exercises that should be tested with Maven here
        List<ProgrammingLanguage> programmingLanguages = List.of(ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN);
        for (ProgrammingLanguage language : programmingLanguages) {
            List<ProjectType> projectTypes = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language).getProjectTypes();
            if (projectTypes.isEmpty()) {
                argumentBuilder.add(Arguments.of(language, null));
            }
            for (ProjectType projectType : projectTypes) {
                argumentBuilder.add(Arguments.of(language, projectType));
            }
        }
        return argumentBuilder.build();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void test_template_exercise(ProgrammingLanguage language, ProjectType projectType) throws Exception {
        runTests(language, projectType, exerciseRepo, TestResult.FAILED);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @MethodSource("languageTypeBuilder")
    void test_template_solution(ProgrammingLanguage language, ProjectType projectType) throws Exception {
        runTests(language, projectType, solutionRepo, TestResult.SUCCESSFUL);
    }

    private void runTests(ProgrammingLanguage language, ProjectType projectType, LocalRepository repository, TestResult testResult) throws Exception {
        exercise.setProgrammingLanguage(language);
        exercise.setProjectType(projectType);
        mockConnectorRequestsForSetup(exercise, false);
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(repository);
        int exitCode;
        if (projectType != null && projectType.isGradle()) {
            exitCode = invokeGradle();
        }
        else {
            exitCode = invokeMaven();
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

    private int invokeMaven() throws MavenInvocationException {
        InvocationRequest mvnRequest = new DefaultInvocationRequest();
        mvnRequest.setPomFile(testRepo.localRepoFile);
        mvnRequest.setGoals(List.of("clean", "test"));
        mvnRequest.setShowVersion(true);

        Invoker mvnInvoker = new DefaultInvoker();
        InvocationResult result = mvnInvoker.execute(mvnRequest);

        assertThat(result.getExecutionException()).isNull();
        return result.getExitCode();
    }

    private int invokeGradle() {
        try (ProjectConnection connector = GradleConnector.newConnector().forProjectDirectory(testRepo.localRepoFile).useBuildDistribution().connect()) {
            BuildLauncher launcher = connector.newBuild();
            launcher.forTasks("clean", "test");
            launcher.run();
        }
        catch (Exception e) {
            // printing the cause because this contains the relevant error message (and not a generic one from the connector)
            Log.error("Error occurred while executing Gradle build: " + e.getCause());
            return -1;
        }
        return 0;
    }

    private void moveAssignmentSourcesOf(LocalRepository localRepository) throws IOException {
        Path sourceSrc = localRepository.localRepoFile.toPath().resolve("src");
        Path assignment = testRepo.localRepoFile.toPath().resolve("assignment");
        Files.createDirectories(assignment);
        Files.move(sourceSrc, assignment.resolve("src"));
    }

    private Map<TestResult, Integer> readTestReports(String testResultPath) throws MavenReportException {
        File reportFolder = testRepo.localRepoFile.toPath().resolve(testResultPath).toFile();
        assertThat(reportFolder).as("test reports generated").matches(SurefireReportParser::hasReportFiles);

        // Note that the locale does not have any effect on parsing and is only used in some other methods
        SurefireReportParser reportParser = new SurefireReportParser(List.of(reportFolder), Locale.US, new PrintStreamLogger(System.out));
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
