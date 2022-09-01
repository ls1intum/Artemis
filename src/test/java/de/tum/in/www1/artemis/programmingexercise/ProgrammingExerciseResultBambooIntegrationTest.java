package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
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

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.hestia.TestwiseCoverageTestUtil;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TestConstants;

class ProgrammingExerciseResultBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseResultTestService programmingExerciseResultTestService;

    @BeforeEach
    void setup() {
        programmingExerciseResultTestService.setup();
        bitbucketRequestMockProvider.enableMockingOfRequests();

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        programmingExerciseResultTestService.tearDown();
        bitbucketRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateFeedbackInSemiAutomaticResult() throws Exception {
        var loginName = "student1";
        var planKey = (programmingExerciseResultTestService.getProgrammingExercise().getProjectKey() + "-" + loginName).toUpperCase();
        var notification = ModelFactory.generateBambooBuildResult("assignment", planKey, null, null, List.of("test1"), List.of(), new ArrayList<>());
        programmingExerciseResultTestService.shouldUpdateFeedbackInSemiAutomaticResult(notification, loginName);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() throws JsonProcessingException {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, null, null, null, List.of("test1", "test2", "test4"), List.of(),
                new ArrayList<>());
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExercise().getProjectKey(), TestConstants.COMMIT_HASH_STRING,
                ZonedDateTime.now());
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, false);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResultWithFailedTest() throws JsonProcessingException {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, null, null, null, List.of("test1", "test2", "test4"), List.of("test3"),
                new ArrayList<>());
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExercise().getProjectKey(), TestConstants.COMMIT_HASH_STRING,
                ZonedDateTime.now());
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = "student1", roles = "USER")
    void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(ProgrammingLanguage programmingLanguage) throws JsonProcessingException {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage);
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExerciseWithStaticCodeAnalysis().getProjectKey(),
                TestConstants.COMMIT_HASH_STRING, ZonedDateTime.now());
        var scaReports = notification.getBuild().jobs().get(0).staticCodeAnalysisReports();
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            // SwiftLint has only one category at the moment
            assertThat(scaReports).hasSize(1);
            assertThat(scaReports.get(0).getIssues().get(0).getCategory()).isEqualTo("swiftLint");
        }
        else if (programmingLanguage == ProgrammingLanguage.JAVA) {
            assertThat(scaReports).hasSize(4);
            scaReports.get(0).getIssues().forEach(issue -> assertThat(issue.getCategory()).isNotNull());
        }
        else if (programmingLanguage == ProgrammingLanguage.C) {
            assertThat(scaReports).hasSize(5);
            scaReports.get(0).getIssues().forEach(issue -> assertThat(issue.getCategory()).isNotNull());
        }
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldStoreBuildLogsForSubmission() throws JsonProcessingException {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(null, Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), null, new ArrayList<>());
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExercise().getProjectKey(), TestConstants.COMMIT_HASH_STRING,
                ZonedDateTime.now());
        programmingExerciseResultTestService.shouldStoreBuildLogsForSubmission(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldSaveBuildLogsForSuccessfulBuildInBuildLogRepository() throws JsonProcessingException {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(null, Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), null, new ArrayList<>());
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExercise().getProjectKey(), TestConstants.COMMIT_HASH_STRING,
                ZonedDateTime.now());
        programmingExerciseResultTestService.shouldSaveBuildLogsInBuildLogRepository(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldSaveBuildLogsForFailedBuildInBuildLogRepository() throws JsonProcessingException {
        var resultNotification = ModelFactory.generateBambooBuildResultWithLogs(null, Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of("test2"), null, new ArrayList<>());
        bitbucketRequestMockProvider.mockGetPushDate(programmingExerciseResultTestService.getProgrammingExercise().getProjectKey(), TestConstants.COMMIT_HASH_STRING,
                ZonedDateTime.now());
        programmingExerciseResultTestService.shouldSaveBuildLogsInBuildLogRepository(resultNotification);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, null, null, null, List.of("test1", "test2", "test4"), List.of(),
                new ArrayList<>());
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldGenerateTestwiseCoverageFileReport() throws Exception {
        var resultNotification = TestwiseCoverageTestUtil.generateBambooBuildResultWithCoverage();
        programmingExerciseResultTestService.shouldGenerateTestwiseCoverageFileReports(resultNotification);
    }
}
