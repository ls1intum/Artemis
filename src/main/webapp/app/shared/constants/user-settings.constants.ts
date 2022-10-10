export enum UserSettingsCategory {
    NOTIFICATION_SETTINGS = 'NOTIFICATION_SETTINGS',
}

/**
 * UserSettings Category__SettingsGroup__SettingId
 */
export enum SettingId {
    // notification settings settingIds

    // weekly summary
    NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY = 'notification.weekly-summary.basic-weekly-summary',

    // course wide discussion notification setting group
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = 'notification.course-wide-discussion.new-course-post',
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = 'notification.course-wide-discussion.new-reply-for-course-post',
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST = 'notification.course-wide-discussion.new-announcement-post',

    // exercise notification setting group
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED = 'notification.exercise-notification.exercise-submission-assessed',
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED = 'notification.exercise-notification.exercise-released',
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = 'notification.exercise-notification.exercise-open-for-practice',
    NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL = 'notification.exercise-notification.file-submission-successful',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = 'notification.exercise-notification.new-exercise-post',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = 'notification.exercise-notification.new-reply-for-exercise-post',

    // lecture notification setting group
    NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = 'notification.lecture-notification.attachment-changes',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = 'notification.lecture-notification.new-lecture-post',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = 'notification.lecture-notification.new-reply-for-lecture-post',

    // tutorial group notification setting group
    NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = 'notification.tutorial-group-notification.tutorial-group-registration',
    NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE = 'notification.tutorial-group-notification.tutorial-group-delete-update',

    // tutor notification setting group
    NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION = 'notification.tutor-notification.tutorial-group-registration',
    NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN = 'notification.tutor-notification.tutorial-group-assign-unassign',

    // editor notification setting group
    NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED = 'notification.editor-notification.programming-test-cases-changed',

    // instructor notification setting group
    NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED = 'notification.instructor-notification.course-and-exam-archiving-started',
}
