package de.tum.in.www1.artemis.programmingexercise;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ProgrammingExerciseResultTestService;

class ProgrammingExerciseResultBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseResultTestService programmingExerciseResultTestService;

    @BeforeEach
    void setup() {
        programmingExerciseResultTestService.setup();
    }

    @AfterEach
    void tearDown() {
        programmingExerciseResultTestService.tearDown();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithJavaStaticCodeAnalysisReport() {
        var notification = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification);
    }

    @WithMockUser(value = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithSwiftStaticCodeAnalysisReport() {
        var notification = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.SWIFT);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldStoreBuildLogsForSubmission() {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of());
        programmingExerciseResultTestService.shouldStoreBuildLogsForSubmission(resultNotification);
    }
}
