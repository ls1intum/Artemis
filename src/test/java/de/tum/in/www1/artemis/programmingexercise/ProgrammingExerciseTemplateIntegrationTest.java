package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.invoker.*;
import org.apache.maven.shared.utils.Os;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.util.*;

public class ProgrammingExerciseTemplateIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    private ProgrammingExercise exercise;

    LocalRepository exerciseRepo = new LocalRepository();

    LocalRepository testRepo = new LocalRepository();

    LocalRepository solutionRepo = new LocalRepository();

    @BeforeAll
    public static void detectMavenHome() {
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
    public void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(1, 1, 1);
        Course course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        programmingExerciseTestService.setupRepositoryMocks(exercise, exerciseRepo, solutionRepo, testRepo);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        reset(bambooServer);
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void runTemplateTests_java_exercise() throws Exception {
        mockConnectorRequestsForSetup(exercise);
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(exerciseRepo);
        int exitCode = invokeMaven();
        assertThat(exitCode).isNotEqualTo(0);

        var testResults = readTestReports();
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(TestResult.FAILED, 13));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void runTemplateTests_java_solution() throws Exception {
        mockConnectorRequestsForSetup(exercise);
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(solutionRepo);
        int exitCode = invokeMaven();
        assertThat(exitCode).isEqualTo(0);

        var testResults = readTestReports();
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(TestResult.SUCCESSFUL, 13));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void runTemplateTests_kotlin_exercise() throws Exception {
        exercise.setProgrammingLanguage(ProgrammingLanguage.KOTLIN);

        mockConnectorRequestsForSetup(exercise);
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(exerciseRepo);
        int exitCode = invokeMaven();
        assertThat(exitCode).isNotEqualTo(0);

        var testResults = readTestReports();
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(TestResult.FAILED, 12));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void runTemplateTests_kotlin_solution() throws Exception {
        exercise.setProgrammingLanguage(ProgrammingLanguage.KOTLIN);

        mockConnectorRequestsForSetup(exercise);
        request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        moveAssignmentSourcesOf(solutionRepo);
        int exitCode = invokeMaven();
        assertThat(exitCode).isEqualTo(0);

        var testResults = readTestReports();
        assertThat(testResults).containsExactlyInAnyOrderEntriesOf(Map.of(TestResult.SUCCESSFUL, 12));
    }

    private int invokeMaven() throws MavenInvocationException {
        InvocationRequest mvnRequest = new DefaultInvocationRequest();
        mvnRequest.setPomFile(testRepo.localRepoFile);
        mvnRequest.setGoals(List.of("clean", "test"));

        Invoker mvnInvoker = new DefaultInvoker();
        InvocationResult result = mvnInvoker.execute(mvnRequest);

        assertThat(result.getExecutionException()).isNull();
        return result.getExitCode();
    }

    private void moveAssignmentSourcesOf(LocalRepository localRepository) throws IOException {
        Path sourceSrc = localRepository.localRepoFile.toPath().resolve("src");
        Path assignment = testRepo.localRepoFile.toPath().resolve("assignment");
        Files.createDirectories(assignment);
        Files.move(sourceSrc, assignment.resolve("src"));
    }

    private Map<TestResult, Integer> readTestReports() throws MavenReportException {
        File reportFolder = testRepo.localRepoFile.toPath().resolve("target/surefire-reports").toFile();
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
            if (testCase.hasError())
                return TestResult.ERROR;
            if (testCase.hasFailure())
                return TestResult.FAILED;
            if (testCase.hasSkipped())
                return TestResult.SKIPPED;
            return TestResult.SUCCESSFUL;
        }
    }
}
