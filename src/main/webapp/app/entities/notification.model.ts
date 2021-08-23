import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export enum NotificationType {
    SYSTEM = 'system',
    CONNECTION = 'connection',
    GROUP = 'group',
    SINGLE = 'single',
}

/**
 * Corresponds to the NotificationType(.java) enum in the server
 * Is needed to preserve the origin of the notification
 */
export enum OriginalNotificationType {
    ATTACHMENT_CHANGE = 'ATTACHMENT_CHANGE',
    EXERCISE_CREATED = 'EXERCISE_CREATED',
    EXERCISE_PRACTICE = 'EXERCISE_PRACTICE',
    QUIZ_EXERCISE_STARTED = 'QUIZ_EXERCISE_STARTED',
    EXERCISE_UPDATED = 'EXERCISE_UPDATED',
    NEW_ANSWER_POST_FOR_EXERCISE = 'NEW_ANSWER_POST_FOR_EXERCISE',
    NEW_ANSWER_POST_FOR_LECTURE = 'NEW_ANSWER_POST_FOR_LECTURE',
    NEW_POST_FOR_EXERCISE = 'NEW_POST_FOR_EXERCISE',
    NEW_POST_FOR_LECTURE = 'NEW_POST_FOR_LECTURE',
    COURSE_ARCHIVE_STARTED = 'COURSE_ARCHIVE_STARTED',
    COURSE_ARCHIVE_FINISHED = 'COURSE_ARCHIVE_FINISHED',
    COURSE_ARCHIVE_FAILED = 'COURSE_ARCHIVE_FAILED',
    DUPLICATE_TEST_CASE = 'DUPLICATE_TEST_CASE',
    EXAM_ARCHIVE_STARTED = 'EXAM_ARCHIVE_STARTED',
    EXAM_ARCHIVE_FINISHED = 'EXAM_ARCHIVE_FINISHED',
    EXAM_ARCHIVE_FAILED = 'EXAM_ARCHIVE_FAILED',
    ILLEGAL_SUBMISSION = 'ILLEGAL_SUBMISSION',
    UNSPECIFIED = 'UNSPECIFIED',
}

export class Notification implements BaseEntity {
    public id?: number;
    public notificationType?: NotificationType;
    public originalNotificationType?: OriginalNotificationType;
    public title?: string;
    public text?: string;
    public notificationDate?: Moment;
    public target?: string;
    public author?: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}
