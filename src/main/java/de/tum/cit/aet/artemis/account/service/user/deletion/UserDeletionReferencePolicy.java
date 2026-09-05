package de.tum.cit.aet.artemis.account.service.user.deletion;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executable policy for every direct foreign key to {@code jhi_user}.
 * <p>
 * The schema-completeness test fails when a new direct user reference is introduced without an explicit lifecycle
 * decision. {@code automaticBlocker} distinguishes business-domain data, which must be removed by its owning retention
 * cleanup, from account-scoped rows that may be removed with the account.
 */
public enum UserDeletionReferencePolicy {

    ANSWER_POST_AUTHOR("answer_post", "author_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DELETE, true),
    ANSWER_POST_VERIFIER("answer_post", "verified_by_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DETACH_ACTOR, true),
    ASSESSMENT_NOTE_CREATOR("assessment_note", "creator_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DETACH_ACTOR, true),
    CALENDAR_SUBSCRIPTION("calendar_subscription_token_store", "jhi_user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    COMPETENCY_PROGRESS("competency_user", "user_id", UserDeletionDataCategory.LEARNING_ANALYTICS, UserDeletionAction.DELETE, true),
    COMPLAINT_REVIEWER("complaint_response", "reviewer_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DETACH_ACTOR, true),
    COMPLAINT_STUDENT("complaint", "student_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DELETE, true),
    CONDUCT_AGREEMENT("conduct_agreement", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    CONVERSATION_MEMBERSHIP("conversation_participant", "user_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.REMOVE_MEMBERSHIP, true),
    CONVERSATION_CREATOR("conversation", "creator_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DETACH_ACTOR, true),
    COURSE_REQUEST("course_request", "requester_id", UserDeletionDataCategory.COURSE_REQUEST, UserDeletionAction.DELETE, true),
    DATA_EXPORT("data_export", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    EXAM_REGISTRATION("exam_user", "student_id", UserDeletionDataCategory.EXAM, UserDeletionAction.DELETE, true),
    EXERCISE_VERSION_AUTHOR("exercise_version", "author_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DELETE, true),
    GLOBAL_NOTIFICATION_SETTING("global_notification_setting", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    IRIS_SESSION("iris_session", "user_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DELETE, true),
    AUTHORITY("jhi_user_authority", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.REMOVE_MEMBERSHIP, false),
    LECTURE_PROGRESS("lecture_unit_user", "user_id", UserDeletionDataCategory.LEARNING_ANALYTICS, UserDeletionAction.DELETE, true),
    LLM_USAGE_ACTOR("llm_token_usage_trace", "user_id", UserDeletionDataCategory.LEARNING_ANALYTICS, UserDeletionAction.DETACH_ACTOR, true),
    LTI_LAUNCH("lti_resource_launch", "user_id", UserDeletionDataCategory.LTI, UserDeletionAction.DELETE, true),
    PARTICIPANT_SCORE("participant_score", "user_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DELETE, true),
    PARTICIPATION_TOKEN("participation_vcs_access_token", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    PARTICIPATION("participation", "student_id", UserDeletionDataCategory.PARTICIPATION, UserDeletionAction.DELETE, true),
    PASSKEY("passkey_credential", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    PLAGIARISM_CASE_STUDENT("plagiarism_case", "student_id", UserDeletionDataCategory.PLAGIARISM, UserDeletionAction.DELETE, true),
    PLAGIARISM_VERDICT_AUTHOR("plagiarism_case", "verdict_by_id", UserDeletionDataCategory.PLAGIARISM, UserDeletionAction.DETACH_ACTOR, true),
    POST_AUTHOR("post", "author_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DELETE, true),
    PUSH_NOTIFICATION_DEVICE("push_notification_device_configuration", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    QUIZ_QUESTION_PROGRESS("quiz_question_progress", "user_id", UserDeletionDataCategory.LEARNING_ANALYTICS, UserDeletionAction.DELETE, true),
    QUIZ_TRAINING_LEADERBOARD("quiz_training_leaderboard", "user_id", UserDeletionDataCategory.LEARNING_ANALYTICS, UserDeletionAction.DELETE, true),
    REACTION_AUTHOR("reaction", "user_id", UserDeletionDataCategory.COMMUNICATION, UserDeletionAction.DELETE, true),
    REPOSITORY_TOKEN("repository_vcs_access_token", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    RESULT_ASSESSOR("result", "assessor_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DETACH_ACTOR, true),
    REVIEW_COMMENT_AUTHOR("review_comment", "author_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DETACH_ACTOR, true),
    SAVED_POST("saved_post", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    STUDENT_EXAM("student_exam", "user_id", UserDeletionDataCategory.EXAM, UserDeletionAction.DELETE, true),
    SUBMISSION_VERSION_AUTHOR("submission_version", "author_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DELETE, true),
    TEAM_MEMBERSHIP("team_student", "student_id", UserDeletionDataCategory.TEAM, UserDeletionAction.REMOVE_MEMBERSHIP, true),
    TEAM_OWNER("team", "owner_id", UserDeletionDataCategory.TEAM, UserDeletionAction.DETACH_ACTOR, true),
    TUTOR_PARTICIPATION("tutor_participation", "tutor_id", UserDeletionDataCategory.ASSESSMENT, UserDeletionAction.DELETE, true),
    TUTORIAL_GROUP_REGISTRATION("tutorial_group_registration", "student_id", UserDeletionDataCategory.TUTORIAL_GROUP, UserDeletionAction.REMOVE_MEMBERSHIP, true),
    TUTORIAL_GROUP_TEACHING_ASSISTANT("tutorial_group", "teaching_assistant_id", UserDeletionDataCategory.TUTORIAL_GROUP, UserDeletionAction.DETACH_ACTOR, true),
    USER_ACTIVITY("user_activity", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    USER_AI_PREFERENCE("user_ai_preference", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    COURSE_NOTIFICATION_PRESET("user_course_notification_setting_preset", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    COURSE_NOTIFICATION_SPECIFICATION("user_course_notification_setting_specification", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    COURSE_NOTIFICATION_STATUS("user_course_notification_status", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    COURSE_ROLE("user_course_role", "user_id", UserDeletionDataCategory.COURSE_MEMBERSHIP, UserDeletionAction.REMOVE_MEMBERSHIP, true),
    IDE_MAPPING("user_ide_mapping", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    USER_LTI_IDENTITY("user_lti", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    ORGANIZATION_MEMBERSHIP("user_organization", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.REMOVE_MEMBERSHIP, false),
    SSH_PUBLIC_KEY("user_public_ssh_key", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    RECOVERY_KEY("user_recovery_key", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false),
    PERSONAL_VCS_TOKEN("user_vcs_access_token", "user_id", UserDeletionDataCategory.ACCOUNT, UserDeletionAction.DELETE, false);

    private final String tableName;

    private final String columnName;

    private final UserDeletionDataCategory category;

    private final UserDeletionAction action;

    private final boolean automaticBlocker;

    UserDeletionReferencePolicy(String tableName, String columnName, UserDeletionDataCategory category, UserDeletionAction action, boolean automaticBlocker) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.category = category;
        this.action = action;
        this.automaticBlocker = automaticBlocker;
    }

    public String tableName() {
        return tableName;
    }

    public String columnName() {
        return columnName;
    }

    public UserDeletionDataCategory category() {
        return category;
    }

    public UserDeletionAction action() {
        return action;
    }

    public boolean automaticBlocker() {
        return automaticBlocker;
    }

    public String referenceKey() {
        return tableName + "." + columnName;
    }

    public static Map<String, UserDeletionReferencePolicy> byReferenceKey() {
        return Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(UserDeletionReferencePolicy::referenceKey, Function.identity()));
    }
}
