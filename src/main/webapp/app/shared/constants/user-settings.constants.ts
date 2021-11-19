export enum UserSettingsCategory {
    NOTIFICATION_SETTINGS = 'NOTIFICATION_SETTINGS',
}

/**
 * UserSettings Category__SettingsGroup__SettingId
 */
export enum SettingId {
    // notification settings settingIds

    // course wide discussion notification setting group
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = 'notification.course-wide-discussion.new-course-post',
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = 'notification.course-wide-discussion.new-reply-for-course-post',
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST = 'notification.course-wide-discussion.new-announcement-post',

    // exercise notification setting group
    NOTIFICATION__EXERCISE_NOTIFICATIONS__EXERCISE_RELEASED = 'notification.exercise-notifications.exercise-released',
    NOTIFICATION__EXERCISE_NOTIFICATIONS__EXERCISE_OPEN_FOR_PRACTICE = 'notification.exercise-notifications.exercise-open-for-practice',
    NOTIFICATION__EXERCISE_NOTIFICATIONS__FILE_SUBMISSION_SUCCESSFUL = 'notification.exercise-notifications.file-submission-successful',
    NOTIFICATION__EXERCISE_NOTIFICATIONS__NEW_EXERCISE_POST = 'notification.exercise-notifications.new-exercise-post',
    NOTIFICATION__EXERCISE_NOTIFICATIONS__NEW_REPLY_FOR_EXERCISE_POST = 'notification.exercise-notifications.new-reply-for-exercise-post',

    // lecture notification setting group
    NOTIFICATION__LECTURE_NOTIFICATIONS__ATTACHMENT_CHANGES = 'notification.lecture-notifications.attachment-changes',
    NOTIFICATION__LECTURE_NOTIFICATIONS__NEW_LECTURE_POST = 'notification.lecture-notifications.new-lecture-post',
    NOTIFICATION__LECTURE_NOTIFICATIONS__NEW_REPLY_FOR_LECTURE_POST = 'notification.lecture-notifications.new-reply-for-lecture-post',

    // instructor notification setting group
    NOTIFICATION__INSTRUCTOR_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = 'notification.instructor-notification.course-and-exam-archiving-started',
}
