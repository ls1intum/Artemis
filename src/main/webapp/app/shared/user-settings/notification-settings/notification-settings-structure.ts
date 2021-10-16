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
                    key: 'exerciseCreatedOrStarted',
                    descriptionKey: 'exerciseCreatedOrStartedDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED,
                },
                {
                    key: 'exerciseOpenForPractice',
                    descriptionKey: 'exerciseOpenForPracticeDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                },
                {
                    key: 'newExercisePost',
                    descriptionKey: 'newExercisePostDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_EXERCISE_POST,
                },
                {
                    key: 'newReplyForExercisePost',
                    descriptionKey: 'newReplyForExercisePostDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST,
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
                    key: 'newLecturePost',
                    descriptionKey: 'newLecturePostDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_LECTURE_POST,
                },
                {
                    key: 'newReplyForLecturePost',
                    descriptionKey: 'newReplyForLecturePostDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__NEW_REPLY_FOR_LECTURE_POST,
                },
            ],
        },
        {
            key: 'courseWideDiscussionNotifications',
            restrictionLevel: Authority.USER,
            settings: [
                {
                    key: 'newCoursePost',
                    descriptionKey: 'newCoursePostDescription',
                    settingId: SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_COURSE_POST,
                },
                {
                    key: 'newReplyForCoursePost',
                    descriptionKey: 'newReplyForCoursePostDescription',
                    settingId: SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_REPLY_FOR_COURSE_POST,
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
