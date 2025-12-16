package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory.DEFAULT_BRANCH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.TestConstants;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.service.ci.notification.dto.CommitDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class ProgrammingExerciseResultJenkinsIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "progexresultjenk";

    @BeforeEach
    void setup() throws GitAPIException, IOException {
        programmingExerciseResultTestService.setup(TEST_PREFIX);
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseResultTestService.tearDown();
    }

    private String getRepoName(ProgrammingExercise exercise, String userLogin) {
        return (exercise.getProjectKey() + "-" + userLogin).toUpperCase();
    }

    private String getFolderName(ProgrammingExercise exercise, String repoName) {
        // The full name is specified as <FOLDER NAME> » <JOB NAME> <Build Number>
        return exercise.getProjectKey() + " » " + repoName + " #3";
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldUpdateFeedbackInSemiAutomaticResult() throws Exception {
        var loginName = TEST_PREFIX + "student1";
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var repoName = getRepoName(exercise, loginName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(getFolderName(exercise, repoName), repoName, null, exercise.getProgrammingLanguage(), false,
                List.of("test1"), List.of(), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldUpdateFeedbackInSemiAutomaticResult(notification, loginName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() {
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true,
                List.of("test1", "test2", "test4"), List.of(), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResultWithFailedTests() {
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true,
                List.of("test1", "test2", "test4"), List.of("test3"), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult(notification, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(ProgrammingLanguage programmingLanguage) throws GitAPIException, IOException {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, programmingLanguage, true, List.of("test1"), List.of(),
                new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStoreFeedbackForResultWithStaticCodeAnalysisReportAndCustomTestMessages(ProgrammingLanguage programmingLanguage) throws GitAPIException, IOException {
        programmingExerciseResultTestService.setupForProgrammingLanguage(programmingLanguage);
        var notification = ProgrammingExerciseFactory.generateTestResultsDTOWithCustomFeedback(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(), programmingLanguage,
                true);
        programmingExerciseResultTestService.shouldStoreFeedbackForResultWithStaticCodeAnalysisReport(notification, programmingLanguage);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldGenerateNewManualResultIfManualAssessmentExists() {
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true,
                List.of("test1", "test2", "test4"), List.of(), new ArrayList<>(), new ArrayList<>(), null);
        programmingExerciseResultTestService.shouldGenerateNewManualResultIfManualAssessmentExists(notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectCommitsWithoutCommitHash() {
        // Dummy needed to keep the list modifiable, then use clear to create a notification without a commit hash
        CommitDTO dummy = new CommitDTO("", "", "");
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.ASSIGNMENT_REPO_NAME, null, ProgrammingLanguage.JAVA, true,
                List.of("test1", "test2", "test4"), List.of(), new ArrayList<>(), new ArrayList<>(List.of(dummy)), null);
        notification.commits().clear();
        programmingExerciseResultTestService.shouldRejectNotificationWithoutCommitHash(notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIgnoreResultOnOtherBranches() {
        var commit = new CommitDTO("abc123", "slug", "other");
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(),
                List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldIgnoreResultIfNotOnDefaultBranch(notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateResultOnParticipationDefaultBranch() {
        var commit = new CommitDTO("commit1", "slug", "branch");
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, TEST_PREFIX + "student1", null, ProgrammingLanguage.JAVA, false, List.of(), List.of(), List.of(),
                List.of(commit), null);
        programmingExerciseResultTestService.shouldCreateResultOnParticipationDefaultBranch(notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIgnoreResultNotOnParticipationDefaultBranch() {
        var commit = new CommitDTO("abc123", "slug", "other");
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, TEST_PREFIX + "student1", null, ProgrammingLanguage.JAVA, false, List.of(), List.of(), List.of(),
                List.of(commit), null);
        programmingExerciseResultTestService.shouldIgnoreResultIfNotOnParticipationBranch(notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateResultOnDefaultBranch() {
        var commit = new CommitDTO("commit1", "slug", DEFAULT_BRANCH);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(),
                List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldCreateResultOnCustomDefaultBranch(defaultBranch, notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateResultOnCustomDefaultBranch() {
        final var customDefaultBranch = "dummy";
        var commit = new CommitDTO("commit1", "slug", customDefaultBranch);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(null, Constants.SOLUTION_REPO_NAME, null, ProgrammingLanguage.JAVA, false, List.of(), List.of(),
                List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldCreateResultOnCustomDefaultBranch(customDefaultBranch, notification);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCorrectlyNotifyStudentsAboutNewResults() throws Exception {
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var commit = new CommitDTO("commit1", "slug", DEFAULT_BRANCH);
        String repoName = getRepoName(exercise, TEST_PREFIX + "student1");
        String folderName = getFolderName(exercise, repoName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(folderName, repoName, null, ProgrammingLanguage.JAVA, false, List.of("test1", "test2"),
                List.of("test3"), List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldCorrectlyNotifyStudentsAboutNewResults(notification, websocketMessagingService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCorrectlyNotifyStudentsAboutNewResultsInOngoingExamExercise() throws Exception {
        programmingExerciseResultTestService.setupProgrammingExerciseForExam(false);
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var commit = new CommitDTO("commit1", "slug", DEFAULT_BRANCH);
        String repoName = getRepoName(exercise, TEST_PREFIX + "student1");
        String folderName = getFolderName(exercise, repoName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(folderName, repoName, null, ProgrammingLanguage.JAVA, false, List.of("test1", "test2"),
                List.of("test3"), List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldCorrectlyNotifyStudentsAboutNewResults(notification, websocketMessagingService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotNotifyStudentsAboutNewResultsInEndedExamExercise() throws Exception {
        programmingExerciseResultTestService.setupProgrammingExerciseForExam(true);
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var commit = new CommitDTO("commit1", "slug", DEFAULT_BRANCH);
        String repoName = getRepoName(exercise, TEST_PREFIX + "student1");
        String folderName = getFolderName(exercise, repoName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(folderName, repoName, null, ProgrammingLanguage.JAVA, false, List.of("test1", "test2"),
                List.of("test3"), List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldNotNotifyStudentsAboutNewResults(notification, websocketMessagingService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRemoveTestCaseNamesFromWebsocketNotification() throws Exception {
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var commit = new CommitDTO("commit1", "slug", DEFAULT_BRANCH);
        String repoName = getRepoName(exercise, TEST_PREFIX + "student1");
        String folderName = getFolderName(exercise, repoName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(folderName, repoName, null, ProgrammingLanguage.JAVA, false, List.of("test1", "test2"),
                List.of("test3", "test4"), List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldRemoveTestCaseNamesFromWebsocketNotification(notification, websocketMessagingService);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUpdateParticipantScoresOnlyOnce() {
        var exercise = programmingExerciseResultTestService.getProgrammingExercise();
        var commit = new CommitDTO(TestConstants.COMMIT_HASH_STRING, "slug", DEFAULT_BRANCH);
        String repoName = getRepoName(exercise, TEST_PREFIX + "student1");
        String folderName = getFolderName(exercise, repoName);
        var notification = ProgrammingExerciseFactory.generateTestResultDTO(folderName, repoName, null, ProgrammingLanguage.JAVA, false, List.of("test1", "test2"),
                List.of("test3", "test4"), List.of(), List.of(commit), null);
        programmingExerciseResultTestService.shouldUpdateParticipantScoresOnlyOnce(notification, instanceMessageSendService);
    }
}
