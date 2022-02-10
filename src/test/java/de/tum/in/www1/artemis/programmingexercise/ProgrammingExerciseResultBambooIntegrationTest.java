package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingExerciseResultBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
        var notification = ModelFactory.generateBambooBuildResult("assignment", List.of("test1"), List.of());
        var loginName = "student1";
        var planKey = (programmingExerciseResultTestService.getProgrammingExercise().getProjectKey() + "-" + loginName).toUpperCase();
        notification.getPlan().setKey(planKey);
        programmingExerciseResultTestService.shouldUpdateFeedbackInSemiAutomaticResult(notification, loginName);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, false);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResultWithFailedTest() {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of("test3"));
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(ProgrammingLanguage programmingLanguage) {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage);
        var scaReports = notification.getBuild().getJobs().get(0).getStaticCodeAnalysisReports();
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            // SwiftLint has only one category at the moment
            assertThat(scaReports.size()).isEqualTo(1);
            assertThat(scaReports.get(0).getIssues().get(0).getCategory()).isEqualTo("swiftLint");
        }
        else if (programmingLanguage == ProgrammingLanguage.JAVA) {
            assertThat(scaReports.size()).isEqualTo(4);
            scaReports.get(0).getIssues().forEach(issue -> assertThat(issue.getCategory()).isNotNull());
        }
        else if (programmingLanguage == ProgrammingLanguage.C) {
            assertThat(scaReports.size()).isEqualTo(5);
            scaReports.get(0).getIssues().forEach(issue -> assertThat(issue.getCategory()).isNotNull());
        }
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldStoreBuildLogsForSubmission() {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of());
        programmingExerciseResultTestService.shouldStoreBuildLogsForSubmission(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldSaveBuildLogsForSuccessfulBuildInBuildLogRepository() {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of());
        programmingExerciseResultTestService.shouldSaveBuildLogsInBuildLogRepository(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldSaveBuildLogsForFailedBuildInBuildLogRepository() {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of("test2"));
        programmingExerciseResultTestService.shouldSaveBuildLogsInBuildLogRepository(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }
}
