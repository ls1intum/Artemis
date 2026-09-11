package de.tum.cit.aet.artemis.account.service.user.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.UserDeletionImpactDTO;
import de.tum.cit.aet.artemis.account.dto.UserDeletionResultStatus;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Puts a row behind every single reference and then deletes the account.
 *
 * <p>
 * {@link UserDeletionDataCoverageTest} does this for the twenty references an account picks up by being used normally,
 * which is the realistic case but leaves the rest of the catalogue counted only against empty tables. A count that
 * names the wrong field returns nothing there, and nothing would notice - so this test writes one row for each of the
 * fifty-four references directly and insists that every count finds it.
 *
 * <p>
 * The rows are written as SQL rather than through entities on purpose. What is under test is whether the queries reach
 * the rows the catalogue says they own, and the catalogue addresses them by table and column: taking those two straight
 * from {@link UserDeletionReferencePolicy} means the fixture cannot drift from the policy it is meant to exercise, and
 * a renamed table fails here rather than silently covering nothing. Only the further columns a row cannot be written
 * without are named here, and they are what the schema demands, so the build fails when that changes.
 */
class UserDeletionEveryReferenceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "everyreference";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // The pool runs without auto-commit, so a statement written straight through JDBC needs a transaction to reach
    // the database at all. Only the fixture uses it; the deletion under test brings its own repository transactions.
    @Autowired
    private TransactionTemplate transactionTemplate;

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

    private User target;

    private User bystander;

    private Course course;

    @BeforeEach
    void setUp() {
        target = userUtilService.createAndSaveUser(TEST_PREFIX + "target");
        bystander = userUtilService.createAndSaveUser(TEST_PREFIX + "bystander");
        course = courseUtilService.addEmptyCourse();
    }

    @Test
    void everyReferenceIsCountedFromARealRowAndIsGoneAfterwards() {
        transactionTemplate.executeWithoutResult(status -> seedOneRowForEveryReference());

        Map<UserDeletionReferencePolicy, Long> before = counts(target);
        assertThat(before.keySet()).as("every reference has to be found by its own count query, or the impact an administrator confirms understates what is removed")
                .containsExactlyInAnyOrder(UserDeletionReferencePolicy.values());
        assertThat(before.values()).allSatisfy(count -> assertThat(count).isPositive());

        // The fingerprint covers the authorities the account holds, and the fixture granted it one, so the impact has
        // to be built from the account as the deletion itself reads it rather than from the stale object in this test.
        long targetId = target.getId();
        User reloaded = userTestRepository.findByIdForDeletion(targetId).orElseThrow();
        UserDeletionImpactDTO impact = userDeletionPlanService.createImpact(reloaded, UserDeletionMode.ADMIN_FORCED);
        assertThat(impact.totalAffectedObjects()).isEqualTo(before.values().stream().mapToLong(Long::longValue).sum());
        assertThat(permanentUserDeletionService.deleteByAdmin(targetId, impact.impactFingerprint(), "an-admin").status()).isEqualTo(UserDeletionResultStatus.DELETED);

        assertThat(userTestRepository.findById(targetId)).isEmpty();
        assertThat(counts(target)).as("nothing may be left pointing at the deleted account").isEmpty();
        assertThat(userTestRepository.findById(bystander.getId())).as("the account that shares the seeded parents survives").isPresent();
    }

    private Map<UserDeletionReferencePolicy, Long> counts(User user) {
        Map<UserDeletionReferencePolicy, Long> counts = new LinkedHashMap<>();
        for (UserDeletionReferencePolicy policy : UserDeletionReferencePolicy.values()) {
            userReferenceCleanupService.count(policy, List.of(user.getId())).forEach((userId, count) -> counts.put(policy, count));
        }
        return counts;
    }

    /**
     * Writes one row for every reference, on top of the parents each of them needs.
     */
    private void seedOneRowForEveryReference() {
        long userId = target.getId();
        long courseId = course.getId();
        // One reading, so the two timestamps cannot come from different instants.
        Instant seededAt = Instant.now();
        Timestamp now = Timestamp.from(seededAt);
        Timestamp inSixMonths = Timestamp.from(seededAt.plus(180, ChronoUnit.DAYS));

        long exerciseId = insert("exercise", values("discriminator", "T", "title", "Exercise", "course_id", courseId));
        long participationId = insert("participation", values("discriminator", "SP", "exercise_id", exerciseId, "student_id", bystander.getId()));
        long submissionId = insert("submission", values("discriminator", "T", "participation_id", participationId));
        long resultId = insert("result", values("submission_id", submissionId, "exercise_id", exerciseId));
        long complaintResultId = insert("result", values("submission_id", submissionId, "exercise_id", exerciseId));
        long complaintId = insert("complaint", values("result_id", complaintResultId, "complaint_type", "COMPLAINT", "exercise_id", exerciseId, "student_id", bystander.getId()));
        long conversationId = insert("conversation", values("discriminator", "C", "course_id", courseId, "creation_date", now, "name", "channel", "is_course_wide", true));
        long postId = insert("post", values("author_id", bystander.getId(), "creation_date", now, "conversation_id", conversationId));
        long examId = insert("exam", values("title", "Exam", "course_id", courseId, "working_time", 3600));
        long teamId = insert("team", values("exercise_id", exerciseId, "short_name", "tm", "created_by", "test", "owner_id", bystander.getId()));
        long tutorialGroupId = insert("tutorial_group", values("title", "Tutorial", "course_id", courseId));
        long lectureId = insert("lecture", values("course_id", courseId, "title", "Lecture"));
        long lectureUnitId = insert("lecture_unit", values("discriminator", "T", "lecture_id", lectureId, "lecture_unit_order", 0));
        long competencyId = insert("competency", values("discriminator", "C", "title", "Competency", "course_id", courseId, "mastery_threshold", 50));
        long quizQuestionId = insert("quiz_question", values("discriminator", "SA", "title", "Question"));
        long organizationId = insert("organization", values("email_pattern", ".*", "name", TEST_PREFIX + "org", "short_name", TEST_PREFIX));
        long ideId = insert("ide", values("name", "IDE", "deep_link", "ide://open"));
        long courseNotificationId = insert("course_notification", values("course_id", courseId, "type", 1, "creation_date", now, "deletion_date", now));
        long threadId = insert("review_comment_thread", values("exercise_id", exerciseId, "initial_line_number", 1, "target_type", "FILE"));

        // ACCOUNT
        seed(UserDeletionReferencePolicy.CONDUCT_AGREEMENT, userId, values("course_id", courseId));
        seed(UserDeletionReferencePolicy.USER_ACTIVITY, userId, values());
        seed(UserDeletionReferencePolicy.USER_AI_PREFERENCE, userId, values());
        seed(UserDeletionReferencePolicy.RECOVERY_KEY, userId, values());
        seed(UserDeletionReferencePolicy.PERSONAL_VCS_TOKEN, userId, values());
        seed(UserDeletionReferencePolicy.USER_LTI_IDENTITY, userId, values());
        seed(UserDeletionReferencePolicy.DATA_EXPORT, userId, values());
        seed(UserDeletionReferencePolicy.CALENDAR_SUBSCRIPTION, userId, values("token", "calendar-token"));
        seed(UserDeletionReferencePolicy.AUTHORITY, userId, values("authority_name", "ROLE_USER"));
        seed(UserDeletionReferencePolicy.ORGANIZATION_MEMBERSHIP, userId, values("organization_id", organizationId));
        seed(UserDeletionReferencePolicy.SSH_PUBLIC_KEY, userId, values("label", "key", "public_key", "ssh-ed25519 AAAA", "key_hash", TEST_PREFIX + "hash", "creation_date", now));
        seed(UserDeletionReferencePolicy.IDE_MAPPING, userId, values("programming_language", "JAVA", "ide_id", ideId));
        seed(UserDeletionReferencePolicy.PASSKEY, userId, values("credential_id", TEST_PREFIX + "cred", "last_used", now, "created_by", "test", "created_date", now,
                "credential_type", "public-key", "public_key_cose", new byte[] { 1, 2, 3 }));
        seed(UserDeletionReferencePolicy.SAVED_POST, userId, values("post_id", postId));
        seed(UserDeletionReferencePolicy.GLOBAL_NOTIFICATION_SETTING, userId, values("global_notification_type", "NEW_LOGIN"));
        seed(UserDeletionReferencePolicy.PUSH_NOTIFICATION_DEVICE, userId, values("device_type", 0, "token", TEST_PREFIX + "device"));
        seed(UserDeletionReferencePolicy.COURSE_NOTIFICATION_PRESET, userId, values("course_id", courseId, "setting_preset", 1));
        seed(UserDeletionReferencePolicy.COURSE_NOTIFICATION_SPECIFICATION, userId,
                values("course_id", courseId, "course_notification_type", 1, "email", false, "push", false, "webapp", false, "summary", false));
        seed(UserDeletionReferencePolicy.COURSE_NOTIFICATION_STATUS, userId, values("course_notification_id", courseNotificationId, "status", 0));
        seed(UserDeletionReferencePolicy.PARTICIPATION_TOKEN, userId, values("participation_id", participationId, "vcs_access_token", TEST_PREFIX + "pat"));
        seed(UserDeletionReferencePolicy.REPOSITORY_TOKEN, userId,
                values("exercise_id", exerciseId, "repository_type", "USER", "repository_uri", "http://localhost/repo.git", "vcs_access_token", TEST_PREFIX + "rt"));

        // COURSE MEMBERSHIP, COMMUNICATION and the course request
        seed(UserDeletionReferencePolicy.COURSE_ROLE, userId, values("course_id", courseId, "course_role", "STUDENT"));
        // The course request carries a semester and both dates because all three are mandatory on the table.
        seed(UserDeletionReferencePolicy.COURSE_REQUEST, userId, values("title", "Requested course", "short_name", TEST_PREFIX + "req", "reason", "because", "created_date", now,
                "semester", "WS24/25", "start_date", now, "end_date", inSixMonths));
        seed(UserDeletionReferencePolicy.CONVERSATION_MEMBERSHIP, userId, values("conversation_id", conversationId));
        seed(UserDeletionReferencePolicy.CONVERSATION_CREATOR, userId,
                values("discriminator", "C", "course_id", courseId, "creation_date", now, "name", "own", "is_course_wide", true));
        seed(UserDeletionReferencePolicy.POST_AUTHOR, userId, values("creation_date", now, "conversation_id", conversationId));
        seed(UserDeletionReferencePolicy.ANSWER_POST_AUTHOR, userId, values("post_id", postId, "creation_date", now));
        seed(UserDeletionReferencePolicy.ANSWER_POST_VERIFIER, userId, values("post_id", postId, "author_id", bystander.getId(), "creation_date", now));
        seed(UserDeletionReferencePolicy.REACTION_AUTHOR, userId, values("post_id", postId, "emoji_id", "smiley", "creation_date", now));
        seed(UserDeletionReferencePolicy.IRIS_SESSION, userId, values("discriminator", "CHAT", "creation_date", now));

        // EXERCISES, ASSESSMENT and the rest of the course
        seed(UserDeletionReferencePolicy.PARTICIPATION, userId, values("discriminator", "SP", "exercise_id", exerciseId));
        seed(UserDeletionReferencePolicy.PARTICIPANT_SCORE, userId, values("exercise_id", exerciseId, "discriminator", "SS"));
        seed(UserDeletionReferencePolicy.RESULT_ASSESSOR, userId, values("submission_id", submissionId, "exercise_id", exerciseId));
        seed(UserDeletionReferencePolicy.ASSESSMENT_NOTE_CREATOR, userId, values("result_id", resultId));
        seed(UserDeletionReferencePolicy.COMPLAINT_STUDENT, userId, values("result_id", resultId, "complaint_type", "COMPLAINT", "exercise_id", exerciseId));
        seed(UserDeletionReferencePolicy.COMPLAINT_REVIEWER, userId, values("complaint_id", complaintId));
        seed(UserDeletionReferencePolicy.TUTOR_PARTICIPATION, userId, values());
        seed(UserDeletionReferencePolicy.SUBMISSION_VERSION_AUTHOR, userId, values("submission_id", submissionId));
        seed(UserDeletionReferencePolicy.EXERCISE_VERSION_AUTHOR, userId,
                values("exercise_id", exerciseId, "exercise_snapshot", new Json("{}"), "created_by", "test", "created_date", now));
        seed(UserDeletionReferencePolicy.REVIEW_COMMENT_AUTHOR, userId,
                values("thread_id", threadId, "content", new Json("{}"), "created_date", now, "created_by", "test", "type", "COMMENT"));
        seed(UserDeletionReferencePolicy.TEAM_MEMBERSHIP, userId, values("team_id", teamId));
        seed(UserDeletionReferencePolicy.TEAM_OWNER, userId, values("exercise_id", exerciseId, "short_name", "own", "created_by", "test"));

        // EXAM, TUTORIAL GROUP, PLAGIARISM, LTI and the learning analytics
        seed(UserDeletionReferencePolicy.EXAM_REGISTRATION, userId,
                values("exam_id", examId, "did_check_login", false, "did_check_name", false, "did_check_registration_number", false, "did_check_image", false));
        seed(UserDeletionReferencePolicy.STUDENT_EXAM, userId, values("exam_id", examId));
        seed(UserDeletionReferencePolicy.TUTORIAL_GROUP_REGISTRATION, userId, values("tutorial_group_id", tutorialGroupId));
        seed(UserDeletionReferencePolicy.TUTORIAL_GROUP_TEACHING_ASSISTANT, userId, values("title", "Taught", "course_id", courseId));
        seed(UserDeletionReferencePolicy.PLAGIARISM_CASE_STUDENT, userId, values("exercise_id", exerciseId, "created_by", "test"));
        seed(UserDeletionReferencePolicy.PLAGIARISM_VERDICT_AUTHOR, userId, values("exercise_id", exerciseId, "created_by", "test"));
        seed(UserDeletionReferencePolicy.LTI_LAUNCH, userId, values("iss", "https://platform", "sub", "subject", "deployment_id", "deployment", "resource_link_id", "link"));
        seed(UserDeletionReferencePolicy.COMPETENCY_PROGRESS, userId, values("competency_id", competencyId));
        seed(UserDeletionReferencePolicy.LECTURE_PROGRESS, userId, values("lecture_unit_id", lectureUnitId));
        seed(UserDeletionReferencePolicy.QUIZ_QUESTION_PROGRESS, userId, values("quiz_question_id", quizQuestionId, "course_id", courseId, "due_date", now));
        seed(UserDeletionReferencePolicy.QUIZ_TRAINING_LEADERBOARD, userId,
                values("course_id", courseId, "league", 1, "score", 0, "answered_correctly", 0, "answered_wrong", 0, "due_date", now, "streak", 0, "show_in_leaderboard", true));
        seed(UserDeletionReferencePolicy.LLM_USAGE_ACTOR, userId, values());
    }

    /**
     * Makes the given reference point at the account. Creating an account already gives it a few of these - its
     * activity, its preferences - and several of those tables hold one row per account, so an existing row counts.
     */
    private void seed(UserDeletionReferencePolicy policy, long userId, Map<String, Object> otherColumns) {
        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + policy.tableName() + " WHERE " + policy.columnName() + " = ?", Long.class, userId);
        if (existing != null && existing > 0) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(policy.columnName(), userId);
        row.putAll(otherColumns);
        insertInto(policy.tableName(), row);
    }

    private long insert(String table, Map<String, Object> row) {
        insertInto(table, row);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + table, Long.class);
        assertThat(id).as("inserting the %s the fixture builds on failed", table).isNotNull();
        return id;
    }

    private void insertInto(String table, Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.values().stream().map(value -> value instanceof Json ? "CAST(? AS json)" : "?").toList());
        Object[] parameters = row.values().stream().map(value -> value instanceof Json json ? json.value() : value).toArray();
        jdbcTemplate.update("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")", parameters);
    }

    /** A value the database stores as {@code json}, which a plain string parameter is not accepted for. */
    private record Json(String value) {
    }

    private static Map<String, Object> values(Object... columnsAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < columnsAndValues.length; index += 2) {
            row.put((String) columnsAndValues[index], columnsAndValues[index + 1]);
        }
        return row;
    }
}
