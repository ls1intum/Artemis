package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
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
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingExerciseResultJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseResultTestService programmingExerciseResultTestService;

    @BeforeEach
    void setup() {
        programmingExerciseResultTestService.setup();

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        programmingExerciseResultTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateFeedbackInSemiAutomaticResult() throws Exception {
        var loginName = "student1";
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var repoName = (exercise.getProjectKey() + "-" + loginName).toUpperCase();
        var notification = ModelFactory.generateTestResultDTO(repoName, List.of("test1"), List.of(), exercise.getProgrammingLanguage(), false);
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        notification.setFullName(exercise.getProjectKey() + " » " + repoName + " #3");
        programmingExerciseResultTestService.shouldUpdateFeedbackInSemiAutomaticResult(notification, loginName);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of(), ProgrammingLanguage.JAVA, true);
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, false);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResultWithFailedTests() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of("test3"), ProgrammingLanguage.JAVA, true);
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(ProgrammingLanguage programmingLanguage) {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage, true);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReportAndCustomTestMessages(ProgrammingLanguage programmingLanguage) {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateTestResultsDTOWithCustomFeedback(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage, true);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of(), ProgrammingLanguage.JAVA, true);
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }
}
