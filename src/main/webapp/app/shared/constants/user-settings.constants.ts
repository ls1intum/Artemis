export enum UserSettingsCategory {
    NOTIFICATION_SETTINGS = 'NOTIFICATION_SETTINGS',
}

/**
 * UserSettings Category__SettingsGroup__SettingId
 */
export enum SettingId {
    // notification settings settingIds

    // exercise notification setting group
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED = 'notification.exercise-notification.exercise-created-or-started',
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = 'notification.exercise-notification.exercise-open-for-practice',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST = 'notification.exercise-notification.new-exercise-post',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST = 'notification.exercise-notification.new-reply-for-exercise-post',

    // lecture notification setting group
    NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = 'notification.lecture-notification.attachment-changes',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST = 'notification.lecture-notification.new-lecture-post',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST = 'notification.lecture-notification.new-reply-for-lecture-post',

    // course wide discussion notification setting group
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST = 'notification.course-wide-discussion.new-course-post',
    NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST = 'notification.course-wide-discussion.new-reply-for-course-post',

    // instructor exclusive notification setting group
    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = 'notification.instructor-exclusive-notification.course-and-exam-archiving-started',
}
