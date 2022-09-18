package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.util.ModelFactory.DEFAULT_BRANCH;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.CommitDTO;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TestConstants;

class ProgrammingExerciseResultJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseResultTestService programmingExerciseResultTestService;

    @BeforeEach
    void setup() {
        programmingExerciseResultTestService.setup();
        gitlabRequestMockProvider.enableMockingOfRequests();

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        programmingExerciseResultTestService.tearDown();
        gitlabRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateFeedbackInSemiAutomaticResult() throws Exception {
        var loginName = "student1";
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var repoName = (exercise.getProjectKey() + "-" + loginName).toUpperCase();
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        var notification = ModelFactory.generateTestResultDTO(exercise.getProjectKey() + " » " + repoName + " #3", repoName, null, exercise.getProgrammingLanguage(), false,
                List.of("test1"), List.of(), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldUpdateFeedbackInSemiAutomaticResult(notification, loginName);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() throws GitLabApiException {
        var notification = ModelFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true, List.of("test1", "test2", "test4"),
                List.of(), new ArrayList<>(), new ArrayList<>(), null);
        gitlabRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getSolutionParticipation(), Map.of(TestConstants.COMMIT_HASH_STRING, ZonedDateTime.now()));
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, false);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResultWithFailedTests() throws GitLabApiException {
        var notification = ModelFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true, List.of("test1", "test2", "test4"),
                List.of("test3"), new ArrayList<>(), new ArrayList<>(), null);
        gitlabRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getSolutionParticipation(), Map.of(TestConstants.COMMIT_HASH_STRING, ZonedDateTime.now()));
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(ProgrammingLanguage programmingLanguage) {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, programmingLanguage, true, List.of("test1"), List.of(), new ArrayList<>(),
                new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    void shouldStoreFeedbackForResultWithStaticCodeAnalysisReportAndCustomTestMessages(ProgrammingLanguage programmingLanguage) {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateTestResultsDTOWithCustomFeedback(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage, true);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ModelFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true, List.of("test1", "test2", "test4"),
                List.of(), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldIgnoreResultOnOtherBranches() {
        var commit = new CommitDTO("abc123", "slug", "other");
        var notification = ModelFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(), List.of(),
                List.of(commit), null);
        programmingExerciseResultTestService.shouldIgnoreResultIfNotOnDefaultBranch(notification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldCreateResultOnDefaultBranch() {
        var commit = new CommitDTO("abc123", "slug", DEFAULT_BRANCH);
        var notification = ModelFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(), List.of(),
                List.of(commit), null);
        programmingExerciseResultTestService.shouldCreateResultOnCustomDefaultBranch(defaultBranch, notification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldCreateResultOnCustomDefaultBranch() {
        final var customDefaultBranch = "dummy";
        var commit = new CommitDTO("abc123", "slug", customDefaultBranch);
        var notification = ModelFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(), List.of(),
                List.of(commit), null);
        programmingExerciseResultTestService.shouldCreateResultOnCustomDefaultBranch(customDefaultBranch, notification);
    }
}
