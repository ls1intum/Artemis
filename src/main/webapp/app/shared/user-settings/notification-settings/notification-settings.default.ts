import { UserSettings } from 'app/shared/user-settings/user-settings.service';

export const defaultNotificationSettings: UserSettings = {
    category: 'Notification Settings',
    groups: [
        {
            name: 'Exercise Notifications',
            options: [
                {
                    name: 'Exercise created / started',
                    description: 'Get notified if a (new) exercise has been created / started',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.exercise-notification.exercise-created-or-started',
                    },
                },
                {
                    name: 'Exercise open for Practice',
                    description: 'Get notified if an exercise has been opened for practice',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.exercise-notification.exercise-open-for-practice',
                    },
                },
                {
                    name: 'New Post for Exercises',
                    description: 'Get notified if a new post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.exercise-notification.new-post-exercises',
                    },
                },
                {
                    name: 'New Answer Post for Exercises',
                    description: 'Get notified if a new answer post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.exercise-notification.new-answer-post-exercises',
                    },
                },
            ],
        },
        {
            name: 'Lecture Notifications',
            options: [
                {
                    name: 'Attachment Changes',
                    description: 'Receive a notification when an attachment was changed',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.lecture-notification.attachment-changes',
                    },
                },
                {
                    name: 'New Post for Lecture',
                    description: 'Get notified when a new post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.lecture-notification.new-post-for-lecture',
                    },
                },
                {
                    name: 'New Answer Post for Lecture',
                    description: 'Get notified when a new answer post about an exercise has been created',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.lecture-notification.new-answer-post-for-lecture',
                    },
                },
            ],
        },
        {
            name: 'Instructor Exclusive Notifications',
            options: [
                {
                    name: 'Course & Exam Archiving Started',
                    description: 'Receive a notification when the process of archiving a course or exam has been started',
                    optionCore: {
                        webapp: true,
                        email: false,
                        optionSpecifier: 'notification.instructor-exclusive-notification.course-and-exam-archiving-started',
                    },
                },
            ],
        },
    ],
};
