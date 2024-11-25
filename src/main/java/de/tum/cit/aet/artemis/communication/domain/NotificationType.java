package de.tum.cit.aet.artemis.communication.domain;

public enum NotificationType {
    EXERCISE_SUBMISSION_ASSESSED, ATTACHMENT_CHANGE, EXERCISE_RELEASED, EXERCISE_PRACTICE, QUIZ_EXERCISE_STARTED, EXERCISE_UPDATED, NEW_REPLY_FOR_EXERCISE_POST,
    NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXAM_POST, NEW_EXERCISE_POST, NEW_LECTURE_POST, NEW_COURSE_POST, NEW_ANNOUNCEMENT_POST, NEW_EXAM_POST,
    FILE_SUBMISSION_SUCCESSFUL, COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FAILED, PROGRAMMING_TEST_CASES_CHANGED, DUPLICATE_TEST_CASE, EXAM_ARCHIVE_STARTED,
    EXAM_ARCHIVE_FINISHED, EXAM_ARCHIVE_FAILED, ILLEGAL_SUBMISSION, NEW_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT,
    NEW_MANUAL_FEEDBACK_REQUEST, TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR,
    TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_DELETED, TUTORIAL_GROUP_UPDATED, TUTORIAL_GROUP_ASSIGNED,
    TUTORIAL_GROUP_UNASSIGNED, CONVERSATION_NEW_MESSAGE, CONVERSATION_NEW_REPLY_MESSAGE, CONVERSATION_USER_MENTIONED, CONVERSATION_CREATE_ONE_TO_ONE_CHAT,
    CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT, CONVERSATION_ADD_USER_CHANNEL, CONVERSATION_REMOVE_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_CHANNEL,
    CONVERSATION_DELETE_CHANNEL, DATA_EXPORT_CREATED, DATA_EXPORT_FAILED, PROGRAMMING_REPOSITORY_LOCKS, PROGRAMMING_BUILD_RUN_UPDATE, SSH_KEY_ADDED
}
