export enum UserSettingsCategory {
    NOTIFICATION_SETTINGS = 'NOTIFICATION_SETTINGS',
}

/**
 * UserSettings Category__OptionGroup__OptionSpecifier
 */
export enum SettingId {
    // notification settings settingIds

    // exercise notification setting group
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED = 'notification.exercise-notification.exercise-created-or-started',
    NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE = 'notification.exercise-notification.exercise-open-for-practice',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES = 'notification.exercise-notification.new-post-exercises',
    NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES = 'notification.exercise-notification.new-answer-post-exercises',

    // lecture notification settings group
    NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES = 'notification.lecture-notification.attachment-changes',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE = 'notification.lecture-notification.new-post-for-lecture',
    NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE = 'notification.lecture-notification.new-answer-post-for-lecture',

    // lecture notification setting group
    NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED = 'notification.instructor-exclusive-notification.course-and-exam-archiving-started',
}
