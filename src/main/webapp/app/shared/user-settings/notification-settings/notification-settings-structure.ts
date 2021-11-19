import { Authority } from 'app/shared/constants/authority.constants';
import { SettingId, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting, UserSettingsStructure } from '../user-settings.model';

export interface NotificationSetting extends Setting {
    // Status indicating if the settings was activated for a specific communication channel by the user or the default settings
    webapp?: boolean;
    email?: boolean;

    // 'x-Support' indicates if the corresponding checkbox is visible in the UI
    // e.g. announcements should always generate a webapp notification -> webappSupport=false
    // If left undefined webappSupport will count as true/activated (to make the structure file more lightweight)
    webappSupport?: boolean;
    // If left undefined emailSupport will count as false/deactivated
    emailSupport?: boolean;
}

export const notificationSettingsStructure: UserSettingsStructure<NotificationSetting> = {
    category: UserSettingsCategory.NOTIFICATION_SETTINGS,
    groups: [
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
                {
                    key: 'newAnnouncementPost',
                    descriptionKey: 'newAnnouncementPostDescription',
                    settingId: SettingId.NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST,
                    emailSupport: true,
                    webappSupport: false,
                },
            ],
        },
        {
            key: 'exerciseNotifications',
            restrictionLevel: Authority.USER,
            settings: [
                {
                    key: 'exerciseReleased',
                    descriptionKey: 'exerciseReleasedDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATIONS__EXERCISE_RELEASED,
                    emailSupport: true,
                },
                {
                    key: 'exerciseOpenForPractice',
                    descriptionKey: 'exerciseOpenForPracticeDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATIONS__EXERCISE_OPEN_FOR_PRACTICE,
                    emailSupport: true,
                },
                {
                    key: 'fileSubmissionSuccessful',
                    descriptionKey: 'fileSubmissionSuccessfulDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATIONS__FILE_SUBMISSION_SUCCESSFUL,
                },
                {
                    key: 'newExercisePost',
                    descriptionKey: 'newExercisePostDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATIONS__NEW_EXERCISE_POST,
                },
                {
                    key: 'newReplyForExercisePost',
                    descriptionKey: 'newReplyForExercisePostDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATIONS__NEW_REPLY_FOR_EXERCISE_POST,
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
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATIONS__ATTACHMENT_CHANGES,
                    emailSupport: true,
                },
                {
                    key: 'newLecturePost',
                    descriptionKey: 'newLecturePostDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATIONS__NEW_LECTURE_POST,
                },
                {
                    key: 'newReplyForLecturePost',
                    descriptionKey: 'newReplyForLecturePostDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATIONS__NEW_REPLY_FOR_LECTURE_POST,
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
                    settingId: SettingId.NOTIFICATION__INSTRUCTOR_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
                },
            ],
        },
    ],
};
