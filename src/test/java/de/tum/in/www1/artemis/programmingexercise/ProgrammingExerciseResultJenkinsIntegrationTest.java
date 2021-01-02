package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.doReturn;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ProgrammingExerciseResultTestService;

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
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of());
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ModelFactory.generateTestResultDTO(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }
}
