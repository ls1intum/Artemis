package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource.Endpoints.SETUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.connector.gitlab.GitlabRequestMockProvider;
import de.tum.in.www1.artemis.connector.jenkins.JenkinsRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.FeedbackService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.util.*;

class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private JenkinsRequestMockProvider jenkinsRequestMockProvider;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    private ProgrammingExercise exercise;

    private ProgrammingExercise exerciseSaved;

    private StudentParticipation studentParticipation;

    private final static int numberOfStudents = 2;

    private final static String studentLogin = "student1";

    private final static String teamShortName = "team1";

    private LocalRepository exerciseRepo = new LocalRepository();

    private LocalRepository testRepo = new LocalRepository();

    private LocalRepository solutionRepo = new LocalRepository();

    private LocalRepository studentRepo = new LocalRepository();

    private LocalRepository studentTeamRepo = new LocalRepository();

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(numberOfStudents, 1, 1);
        Course course = database.addEmptyCourse();
        exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        exerciseSaved = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        exerciseSaved.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(exerciseSaved);
        database.addStudentParticipationForProgrammingExercise(exerciseSaved, "student1");
        studentParticipation = database.addStudentParticipationForProgrammingExercise(exerciseSaved, "student1");
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();

        exerciseRepo.configureRepos("exerciseLocalRepo", "exerciseOriginRepo");
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        solutionRepo.configureRepos("solutionLocalRepo", "solutionOriginRepo");
        studentRepo.configureRepos("studentRepo", "studentOriginRepo");
        studentTeamRepo.configureRepos("studentTeamRepo", "studentTeamOriginRepo");

        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        String studentRepoName = projectKey.toLowerCase() + "-" + studentLogin;
        String studentTeamRepoName = projectKey.toLowerCase() + "-" + teamShortName;

        var exerciseRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(exerciseRepo.originRepoFile);
        var testRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(testRepo.originRepoFile);
        var solutionRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(solutionRepo.originRepoFile);
        var studentRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentRepo.originRepoFile);
        var studentTeamRepoTestUrl = new GitUtilService.MockFileRepositoryUrl(studentTeamRepo.originRepoFile);

        doReturn(exerciseRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, exerciseRepoName);
        doReturn(testRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, testRepoName);
        doReturn(solutionRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, solutionRepoName);
        doReturn(studentRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentRepoName);
        doReturn(studentTeamRepoTestUrl).when(versionControlService).getCloneRepositoryUrl(projectKey, studentTeamRepoName);

        doReturn(gitService.getRepositoryByLocalPath(exerciseRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(exerciseRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(testRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(testRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(solutionRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(solutionRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentRepoTestUrl.getURL(), true);
        doReturn(gitService.getRepositoryByLocalPath(studentTeamRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(studentTeamRepoTestUrl.getURL(), true);

        doReturn(exerciseRepoName).when(versionControlService).getRepositorySlugFromUrl(exerciseRepoTestUrl.getURL());
        doReturn(testRepoName).when(versionControlService).getRepositorySlugFromUrl(testRepoTestUrl.getURL());
        doReturn(solutionRepoName).when(versionControlService).getRepositorySlugFromUrl(solutionRepoTestUrl.getURL());
        doReturn(studentRepoName).when(versionControlService).getRepositorySlugFromUrl(studentRepoTestUrl.getURL());
        doReturn(studentTeamRepoName).when(versionControlService).getRepositorySlugFromUrl(studentTeamRepoTestUrl.getURL());
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        exerciseRepo.resetLocalRepo();
        testRepo.resetLocalRepo();
        solutionRepo.resetLocalRepo();
        studentRepo.resetLocalRepo();
        studentTeamRepo.resetLocalRepo();
    }

    private static Stream<Arguments> createArgumentsForProgrammingExerciseCreation() {
        return Arrays.stream(ExerciseMode.values()).map(mode -> List.of(Arguments.of(mode, true), Arguments.of(mode, false))).flatMap(Collection::stream);
    }

    @ParameterizedTest
    @MethodSource("createArgumentsForProgrammingExerciseCreation")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_created(ExerciseMode mode, boolean isStaticCodeAnalysisEnabled) throws Exception {
        exercise.setMode(mode);
        exercise.setStaticCodeAnalysisEnabled(isStaticCodeAnalysisEnabled);
        mockConnectorRequestsForSetup(exercise);
        final var generatedExercise = request.postWithResponseBody(ROOT + SETUP, exercise, ProgrammingExercise.class, HttpStatus.CREATED);

        exercise.setId(generatedExercise.getId());
        assertThat(exercise).isEqualTo(generatedExercise);
        assertThat(programmingExerciseRepository.count()).isEqualTo(1);
    }

    // TODO: Don't duplicate tests for jenkins + gitlab. Rather delegate to central test classes
    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of());
        final var staticCodeAnalysisFeedback = feedbackService
                .createFeedbackFromStaticCodeAnalysisReports(ModelFactory.generateStaticCodeAnalysisReports(exerciseSaved.getProgrammingLanguage()));
        final var optionalResult = gradingService.processNewProgrammingExerciseResult(studentParticipation, notification);
        final var savedResult = resultService.findOneWithEagerSubmissionAndFeedback(optionalResult.get().getId());

        // Create comparator to explicitly compare feedback attributes (equals only compares id)
        Comparator<? super Feedback> scaFeedbackComparator = (Comparator<Feedback>) (fb1, fb2) -> {
            if (Objects.equals(fb1.getDetailText(), fb2.getDetailText()) && Objects.equals(fb1.getText(), fb2.getText())
                    && Objects.equals(fb1.getReference(), fb2.getReference())) {
                return 0;
            }
            else {
                return 1;
            }
        };

        assertThat(optionalResult).isPresent();
        var result = optionalResult.get();
        assertThat(result.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(savedResult.getFeedbacks());
        assertThat(savedResult.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(staticCodeAnalysisFeedback);
        assertThat(result.getFeedbacks()).usingElementComparator(scaFeedbackComparator).containsAll(staticCodeAnalysisFeedback);
    }

    private void mockConnectorRequestsForSetup(ProgrammingExercise exercise) throws Exception {
        final var projectKey = exercise.getProjectKey();
        String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        String testRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();
        String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        gitlabRequestMockProvider.mockCheckIfProjectExists(exercise, false);
        gitlabRequestMockProvider.mockCreateProjectForExercise(exercise);
        gitlabRequestMockProvider.mockCreateRepository(exercise, exerciseRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, testRepoName);
        gitlabRequestMockProvider.mockCreateRepository(exercise, solutionRepoName);
        gitlabRequestMockProvider.mockAddAuthenticatedWebHook();
        jenkinsRequestMockProvider.mockCreateProjectForExercise(exercise);
        jenkinsRequestMockProvider.mockCreateBuildPlan(projectKey);
        jenkinsRequestMockProvider.mockTriggerBuild();
    }
}
