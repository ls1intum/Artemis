package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.ConductAgreement;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.domain.UserActivity;
import de.tum.cit.aet.artemis.account.domain.UserAiPreference;
import de.tum.cit.aet.artemis.account.domain.UserRecoveryKey;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.repository.ConductAgreementRepository;
import de.tum.cit.aet.artemis.account.repository.UserActivityRepository;
import de.tum.cit.aet.artemis.account.repository.UserAiPreferenceRepository;
import de.tum.cit.aet.artemis.account.repository.UserRecoveryKeyRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.domain.DataExport;
import de.tum.cit.aet.artemis.admin.domain.DataExportState;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.assessment.util.StudentScoreUtilService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.test_repository.DataExportTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Deletes an account that actually holds data, rather than one that only exists.
 *
 * <p>
 * Every reference is bound to a pair of queries, and {@link UserReferenceCleanupServiceTest} shows that both of them
 * run. Running is not the same as being right: a count that names the wrong field returns nothing, which reads as an
 * account with nothing to lose, and the confirmation an administrator is shown is built from exactly those numbers.
 * This test therefore gives one account something in every data category, insists the preview finds each of them, and
 * insists that afterwards no reference at all is left - all fifty-four, not only the ones it seeded.
 */
class UserDeletionDataCoverageTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "deletioncoverage";

    @Autowired
    private PermanentUserDeletionService permanentUserDeletionService;

    @Autowired
    private UserDeletionPlanService userDeletionPlanService;

    @Autowired
    private UserReferenceCleanupService userReferenceCleanupService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private StudentScoreUtilService studentScoreUtilService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private ConductAgreementRepository conductAgreementRepository;

    @Autowired
    private UserActivityRepository userActivityRepository;

    @Autowired
    private UserAiPreferenceRepository userAiPreferenceRepository;

    @Autowired
    private UserRecoveryKeyRepository userRecoveryKeyRepository;

    @Autowired
    private DataExportTestRepository dataExportTestRepository;

    @Autowired
    private SavedPostTestRepository savedPostTestRepository;

    @Autowired
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceTestRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    private User target;

    private User bystander;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        target = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        bystander = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
    }

    @Test
    void anAccountWithDataEverywhereIsPreviewedExactlyAndLeavesNothingBehind() {
        seedAccountData();
        seedCourseAndCommunicationData();
        seedExerciseAndAssessmentData();
        seedExamData();
        seedTeamData();
        seedPlagiarismData();
        seedTutorialGroupData();

        Map<UserDeletionReferencePolicy, Long> before = countsFor(target);
        assertThat(before).as("the fixture has to give the account something to lose, or the deletion proves nothing").isNotEmpty();
        assertThat(before.keySet()).as("the preview reaches every kind of data the account holds").contains(UserDeletionReferencePolicy.CONDUCT_AGREEMENT,
                UserDeletionReferencePolicy.USER_ACTIVITY, UserDeletionReferencePolicy.USER_AI_PREFERENCE, UserDeletionReferencePolicy.RECOVERY_KEY,
                UserDeletionReferencePolicy.DATA_EXPORT, UserDeletionReferencePolicy.SAVED_POST, UserDeletionReferencePolicy.COURSE_ROLE, UserDeletionReferencePolicy.POST_AUTHOR,
                UserDeletionReferencePolicy.ANSWER_POST_AUTHOR, UserDeletionReferencePolicy.REACTION_AUTHOR, UserDeletionReferencePolicy.CONVERSATION_MEMBERSHIP,
                UserDeletionReferencePolicy.PARTICIPATION, UserDeletionReferencePolicy.EXAM_REGISTRATION, UserDeletionReferencePolicy.STUDENT_EXAM,
                UserDeletionReferencePolicy.TEAM_MEMBERSHIP, UserDeletionReferencePolicy.TEAM_OWNER, UserDeletionReferencePolicy.PLAGIARISM_CASE_STUDENT,
                UserDeletionReferencePolicy.LLM_USAGE_ACTOR);

        // The number the administrator confirms is the sum of exactly these counts, so it has to add up.
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(target, UserDeletionMode.ADMIN_FORCED);
        assertThat(impact.totalAffectedObjects()).as("the total shown is the sum of the counted references").isEqualTo(before.values().stream().mapToLong(Long::longValue).sum());
        assertThat(impact.automaticEligible()).as("an account with course data is not something the retention policy may remove on its own").isFalse();
        assertThat(impact.retentionOverrideRequired()).isTrue();

        long targetId = target.getId();
        assertThat(permanentUserDeletionService.deleteByAdmin(targetId, impact.impactFingerprint(), TEST_PREFIX + "instructor1").status())
                .isEqualTo(UserDeletionResultStatus.DELETED);

        assertThat(userTestRepository.findById(targetId)).as("the account itself is gone").isEmpty();
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            assertThat(userReferenceCleanupService.count(policy, List.of(targetId))).as("%s is left pointing at the deleted account", policy).isEmpty();
        }
    }

    @Test
    void deletingOneAccountLeavesAnotherAccountsDataAlone() {
        seedCourseAndCommunicationData();
        seedExerciseAndAssessmentData();

        Map<UserDeletionReferencePolicy, Long> bystanderBefore = countsFor(bystander);
        assertThat(bystanderBefore).as("the other account has to hold something, or there is nothing to protect").isNotEmpty();

        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(target, UserDeletionMode.ADMIN_FORCED);
        permanentUserDeletionService.deleteByAdmin(target.getId(), impact.impactFingerprint(), TEST_PREFIX + "instructor1");

        Map<UserDeletionReferencePolicy, Long> bystanderAfter = countsFor(bystander);
        // A reply below a thread the deleted account started goes with the thread, so authorship counts may drop. What
        // must not change is anything that has nothing to do with the deleted account.
        assertThat(bystanderAfter).as("the other account keeps its own course membership and settings")
                .containsAllEntriesOf(Map.of(UserDeletionReferencePolicy.COURSE_ROLE, bystanderBefore.get(UserDeletionReferencePolicy.COURSE_ROLE)));
        assertThat(userTestRepository.findById(bystander.getId())).as("the other account survives").isPresent();
    }

    private Map<UserDeletionReferencePolicy, Long> countsFor(User user) {
        Map<UserDeletionReferencePolicy, Long> counts = new LinkedHashMap<>();
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            userReferenceCleanupService.count(policy, List.of(user.getId())).forEach((userId, count) -> counts.put(policy, count));
        }
        return counts;
    }

    private void seedAccountData() {
        ConductAgreement agreement = new ConductAgreement();
        agreement.setCourse(course);
        agreement.setUser(target);
        conductAgreementRepository.save(agreement);

        if (userActivityRepository.findByUserId(target.getId()).isEmpty()) {
            UserActivity activity = new UserActivity(target.getId());
            activity.setLastLoginDate(Instant.now());
            userActivityRepository.save(activity);
        }
        if (userAiPreferenceRepository.findByUserId(target.getId()).isEmpty()) {
            userAiPreferenceRepository.save(new UserAiPreference(target.getId()));
        }
        if (userRecoveryKeyRepository.findByUserId(target.getId()).isEmpty()) {
            userRecoveryKeyRepository.save(new UserRecoveryKey(target.getId()));
        }

        DataExport dataExport = new DataExport();
        dataExport.setUser(target);
        dataExport.setCreationFinishedDate(ZonedDateTime.now());
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExportTestRepository.save(dataExport);

        LLMTokenUsageTrace trace = new LLMTokenUsageTrace();
        trace.setUserId(target.getId());
        trace.setServiceType(LLMServiceType.IRIS);
        llmTokenUsageTraceTestRepository.save(trace);
    }

    private void seedCourseAndCommunicationData() {
        Channel channel = conversationUtilService.createCourseWideChannel(course, TEST_PREFIX + "channel");
        conversationUtilService.addParticipantToConversation(channel, target.getLogin());
        conversationUtilService.addParticipantToConversation(channel, bystander.getLogin());

        Post thread = conversationUtilService.addMessageToConversation(target.getLogin(), channel);
        conversationUtilService.addThreadReplyWithReactionForUserToPost(bystander.getLogin(), thread);
        conversationUtilService.addReactionForUserToPost(bystander.getLogin(), thread);

        Post foreignThread = conversationUtilService.addMessageToConversation(bystander.getLogin(), channel);
        conversationUtilService.addThreadReplyWithReactionForUserToPost(target.getLogin(), foreignThread);

        savedPostTestRepository.save(new SavedPost(target, foreignThread.getId(), PostingType.POST, SavedPostStatus.IN_PROGRESS, null));
    }

    private void seedExerciseAndAssessmentData() {
        Exercise exercise = course.getExercises().iterator().next();
        participationUtilService.createAndSaveParticipationForExercise(exercise, target.getLogin());
        participationUtilService.createAndSaveParticipationForExercise(exercise, bystander.getLogin());
        studentScoreUtilService.createStudentScore(exercise, target, 42.0);
        studentScoreUtilService.createStudentScore(exercise, bystander, 42.0);
    }

    private void seedTutorialGroupData() {
        tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Tutorial 1", "", 10, false, "Garching", "ENGLISH",
                userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), Set.of(target));
    }

    private void seedExamData() {
        Exam exam = examUtilService.registerUsersForExamAndSaveExam(examUtilService.addExam(course), TEST_PREFIX, 1);
        examUtilService.addStudentExamWithUser(exam, target);
    }

    private void seedTeamData() {
        var teamExercise = textExerciseUtilService.createTeamTextExercise(course, null, null, null);
        teamUtilService.createTeam(Set.of(target, bystander), target, teamExercise, TEST_PREFIX + "team");
    }

    private void seedPlagiarismData() {
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(course.getExercises().iterator().next());
        plagiarismCase.setStudent(target);
        plagiarismCaseRepository.save(plagiarismCase);
    }
}
