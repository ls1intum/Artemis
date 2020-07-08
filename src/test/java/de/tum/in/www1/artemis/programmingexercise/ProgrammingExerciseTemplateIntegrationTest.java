package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
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
import de.tum.in.www1.artemis.connector.bamboo.BambooRequestMockProvider;
import de.tum.in.www1.artemis.connector.bitbucket.BitbucketRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.util.*;

public class ProgrammingExerciseTemplateIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private BambooRequestMockProvider bambooRequestMockProvider;

    @Autowired
    private BitbucketRequestMockProvider bitbucketRequestMockProvider;

    private Course course;

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
        database.addUsers(1, 1, 1);
        course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(continuousIntegrationService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());

        doReturn(projectKey).when(versionControlService).getProjectKeyFromUrl(any());
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

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws IOException, URISyntaxException {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        bambooRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        bitbucketRequestMockProvider.mockCreateProjectForExercise(exercise);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        bitbucketRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        bitbucketRequestMockProvider.mockAddWebHooks(exercise);
        bambooRequestMockProvider.mockRemoveAllDefaultProjectPermissions(exercise);
        bambooRequestMockProvider.mockGiveProjectPermissions(exercise);

        doReturn(null).when(bambooServer).publish(any());
    }
}
