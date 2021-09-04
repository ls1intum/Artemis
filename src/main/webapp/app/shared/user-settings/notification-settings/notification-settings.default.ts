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
            name: 'Exercise Notifications',
            restrictionLevel: Authority.USER,
            options: [
                {
                    name: 'Exercise created or started',
                    description: 'Get notified if a new exercise has been created or started',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_CREATED_OR_STARTED,
                    },
                },
                {
                    name: 'Exercise open for Practice',
                    description: 'Get notified if an exercise has been opened for practice',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
                    },
                },
                {
                    name: 'New Post for Exercises',
                    description: 'Get notified if a new post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_POST_EXERCISES,
                    },
                },
                {
                    name: 'New Answer Post for Exercises',
                    description: 'Get notified if a new answer post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__NEW_ANSWER_POST_EXERCISES,
                    },
                },
            ],
        },
        {
            name: 'Lecture Notifications',
            restrictionLevel: Authority.USER,
            options: [
                {
                    name: 'Attachment Changes',
                    description: 'Receive a notification when an attachment was changed',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES,
                    },
                },
                {
                    name: 'New Post for Lecture',
                    description: 'Get notified when a new post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_POST_FOR_LECTURE,
                    },
                },
                {
                    name: 'New Answer Post for Lecture',
                    description: 'Get notified when a new answer post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: OptionSpecifier.NOTIFICATION__LECTURE_NOTIFICATION__NEW_ANSWER_POST_FOR_LECTURE,
                    },
                },
            ],
        },
        {
            name: 'Instructor Exclusive Notifications',
            restrictionLevel: Authority.INSTRUCTOR,
            options: [
                {
                    name: 'Course and Exam Archiving Started',
                    description: 'Receive a notification when the process of archiving a course or exam has been started',
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
