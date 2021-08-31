import { NotificationOptionCore } from 'app/shared/user-settings/notification-settings/notification-settings.default';
import { OptionSpecifier } from 'app/shared/constants/user-settings.constants';

export const notificationOptionCoreA: NotificationOptionCore = {
    id: 1,
    optionSpecifier: OptionSpecifier.NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE,
    webapp: false,
    email: false,
};
export const notificationOptionCoreB: NotificationOptionCore = {
    id: 2,
    optionSpecifier: OptionSpecifier.NOTIFICATION__INSTRUCTOR_EXCLUSIVE_NOTIFICATIONS__COURSE_AND_EXAM_ARCHIVING_STARTED,
    webapp: true,
    email: false,
};

export const notificationOptionCoresForTesting: NotificationOptionCore[] = [notificationOptionCoreA, notificationOptionCoreB];
