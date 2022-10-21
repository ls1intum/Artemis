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
            key: 'weeklySummary',
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'basicWeeklySummary',
                    descriptionKey: 'basicWeeklySummaryDescription',
                    settingId: SettingId.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY,
                    emailSupport: true,
                    webappSupport: false,
                },
            ],
        },
        {
            key: 'courseWideDiscussionNotifications',
            restrictionLevels: [Authority.USER],
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
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'exerciseReleased',
                    descriptionKey: 'exerciseReleasedDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED,
                    emailSupport: true,
                },
                {
                    key: 'exerciseOpenForPractice',
                    descriptionKey: 'exerciseOpenForPracticeDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                    emailSupport: true,
                },
                {
                    key: 'exerciseSubmissionAssessed',
                    descriptionKey: 'exerciseSubmissionAssessedDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED,
                    emailSupport: true,
                },
                {
                    key: 'fileSubmissionSuccessful',
                    descriptionKey: 'fileSubmissionSuccessfulDescription',
                    settingId: SettingId.NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL,
                    emailSupport: true,
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
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'attachmentChanges',
                    descriptionKey: 'attachmentChangesDescription',
                    settingId: SettingId.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES,
                    emailSupport: true,
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
            key: 'tutorialGroupNotifications',
            restrictionLevels: [Authority.USER],
            settings: [
                {
                    key: 'registrationTutorialGroup',
                    descriptionKey: 'registrationTutorialGroupStudentDescription',
                    settingId: SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    emailSupport: true,
                },
                {
                    key: 'tutorialGroupUpdateDelete',
                    descriptionKey: 'tutorialGroupUpdateDeleteDescription',
                    settingId: SettingId.NOTIFICATION__TUTORIAL_GROUP_NOTIFICATION__TUTORIAL_GROUP_DELETE_UPDATE,
                    emailSupport: true,
                },
            ],
        },
        {
            key: 'tutorNotifications',
            restrictionLevels: [Authority.TA, Authority.EDITOR, Authority.INSTRUCTOR],
            settings: [
                {
                    key: 'registrationTutorialGroup',
                    descriptionKey: 'registrationTutorialGroupTutorDescription',
                    settingId: SettingId.NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_REGISTRATION,
                    emailSupport: true,
                },
                {
                    key: 'assignUnassignTutorialGroup',
                    descriptionKey: 'assignUnassignTutorialGroupDescription',
                    settingId: SettingId.NOTIFICATION__TUTOR_NOTIFICATION__TUTORIAL_GROUP_ASSIGN_UNASSIGN,
                    emailSupport: true,
                },
            ],
        },
        {
            key: 'editorNotifications',
            restrictionLevels: [Authority.EDITOR, Authority.INSTRUCTOR],
            settings: [
                {
                    key: 'programmingTestCasesChanged',
                    descriptionKey: 'programmingTestCasesChangedDescription',
                    settingId: SettingId.NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED,
                },
            ],
        },
        {
            key: 'instructorNotifications',
            restrictionLevels: [Authority.INSTRUCTOR],
            settings: [
                {
                    key: 'courseAndExamArchivingStarted',
                    descriptionKey: 'courseAndExamArchivingStartedDescription',
                    settingId: SettingId.NOTIFICATION__INSTRUCTOR_NOTIFICATION__COURSE_AND_EXAM_ARCHIVING_STARTED,
                },
            ],
        },
    ],
};
