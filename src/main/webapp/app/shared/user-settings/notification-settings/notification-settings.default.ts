import { SettingsCategory, UserOption } from 'app/shared/user-settings/user-settings.component';

export const defaultNotificationSettings: SettingsCategory = {
    name: 'Notification Settings',
    groups: [
        {
            name: 'Exercise Notifications',
            options: [
                {
                    name: 'Exercise created / started',
                    description: 'Get notified if a (new) exercise has been created / started',
                    webapp: true,
                },
                {
                    name: 'Exercise open for Practice',
                    description: 'Get notified if an exercise has been opened for practice',
                    webapp: true,
                },
                {
                    name: 'New Post for Exercises',
                    description: 'Get notified if a new post about an exercise has been created',
                    webapp: true,
                },
                {
                    name: 'New Answer Post for Exercises',
                    description: 'Get notified if a new answer post about an exercise has been created',
                    webapp: true,
                },
            ],
        },
        {
            name: 'Lecture Notifications',
            options: [
                {
                    name: 'Attachment Changes',
                    description: 'Receive a notification when an attachment was changed',
                    webapp: true,
                },
                {
                    name: 'New Post for Lecture',
                    description: 'Get notified when a new post about an exercise has been created',
                    webapp: true,
                },
                {
                    name: 'New Answer Post for Lecture',
                    description: 'Get notified when a new answer post about an exercise has been created',
                    webapp: true,
                },
            ],
        },
        {
            name: 'Instructor Exclusive Notifications',
            options: [
                {
                    name: 'Course & Exam Archiving Started',
                    description: 'Receive a notification when the process of archiving a course or exam has been started',
                    webapp: true,
                },
            ],
        },
    ],
};
