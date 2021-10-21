import { Authority } from 'app/shared/constants/authority.constants';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from '../user-settings.model';

export interface NotificationSetting extends Setting {
    webapp?: boolean;
    email?: boolean;
}

export const notificationSettingsStructure: UserSettingsStructure<NotificationSetting> = {
    category: UserSettingsCategory.NOTIFICATION_SETTINGS,
    groups: [
        {
            key: 'exerciseNotifications',
            restrictionLevel: Authority.USER,
            settings: [
                {
                    key: 'exerciseReleased',
                    descriptionKey: 'exerciseReleasedDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED,
                },
                {
                    key: 'exerciseOpenForPractice',
                    descriptionKey: 'exerciseOpenForPracticeDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                },
                {
                    key: 'newPostForExercises',
                    descriptionKey: 'newPostForExercisesDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES,
                },
                {
                    key: 'newReplyForExercises',
                    descriptionKey: 'newReplyForExercisesDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
                },
            ],
        },
        {
            key: 'lectureNotifications',
            restrictionLevel: Authority.USER,
            settings: [
                {
                    key: 'attachmentChanges',
                    descriptionKey: 'attachmentChangesDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES,
                },
                {
                    key: 'newPostForLecture',
                    descriptionKey: 'newPostForLectureDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE,
                },
                {
                    key: 'newReplyForLecture',
                    descriptionKey: 'newReplyForLectureDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE,
                },
            ],
        },
        {
            key: 'instructorExclusiveNotifications',
            restrictionLevel: Authority.INSTRUCTOR,
            settings: [
                {
                    key: 'courseAndExamArchivingStarted',
                    descriptionKey: 'courseAndExamArchivingStartedDescription',
                    settingId: SettingId.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
                },
            ],
        },
    ],
};
