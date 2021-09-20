import { Authority } from 'app/shared/constants/authority.constants';
import { OptionSpecifier, UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { OptionCore, UserSettings } from '../user-settings.model';

export interface NotificationOptionCore extends OptionCore {
    webapp: boolean;
    email?: boolean;
}

export const defaultNotificationSettings: UserSettings<NotificationOptionCore> = {
    category: UserSettingsCategory.NOTIFICATION_SETTINGS,
    groups: [
        {
            key: 'exerciseNotifications',
            restrictionLevel: Authority.USER,
            options: [
                {
                    key: 'exerciseCreatedOrStarted',
                    descriptionKey: 'exerciseCreatedOrStartedDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED,
                    },
                },
                {
                    key: 'exerciseOpenForPractice',
                    descriptionKey: 'exerciseOpenForPracticeDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                    },
                },
                {
                    key: 'newPostForExercises',
                    descriptionKey: 'newPostForExercisesDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES,
                    },
                },
                {
                    key: 'newReplyForExercises',
                    descriptionKey: 'newReplyForExercisesDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
                    },
                },
            ],
        },
        {
            key: 'lectureNotifications',
            restrictionLevel: Authority.USER,
            options: [
                {
                    key: 'attachmentChanges',
                    descriptionKey: 'attachmentChangesDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES,
                    },
                },
                {
                    key: 'newPostForLecture',
                    descriptionKey: 'newPostForLectureDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE,
                    },
                },
                {
                    key: 'newReplyForLecture',
                    descriptionKey: 'newReplyForLectureDescription',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE,
                    },
                },
            ],
        },
        {
            key: 'instructorExclusiveNotifications',
            restrictionLevel: Authority.INSTRUCTOR,
            options: [
                {
                    key: 'courseAndExamArchivingStarted',
                    descriptionKey: 'courseAndExamArchivingStartedDescriptions',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
                    },
                },
            ],
        },
    ],
};
