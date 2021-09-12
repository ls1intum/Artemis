import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';

export enum NotificationType {
    SYSTEM = 'system',
    CONNECTION = 'connection',
    GROUP = 'group',
    SINGLE = 'single',
}

export class Notification implements BaseEntity {
    public id?: number;
    public notificationType?: NotificationType;
    public title?: string;
    public text?: string;
    public notificationDate?: Moment;
    public target?: string;
    public author?: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}

/**
 * Corresponds to the NotificationTitleTypeConstants(.java) constant Strings in the server
 */
export const ATTACHMENT_CHANGE_TITLE = 'Attachment updated';

export const EXERCISE_CREATED_TITLE = 'Exercise created';

export const EXERCISE_PRACTICE_TITLE = 'Exercise open for practice';

export const QUIZ_EXERCISE_STARTED_TITLE = 'Quiz started';

export const EXERCISE_UPDATED_TITLE = 'Exercise updated';

export const DUPLICATE_TEST_CASE_TITLE = 'Duplicate test case was found.';

export const ILLEGAL_SUBMISSION_TITLE = 'Illegal submission of a student.';

export const NEW_POST_FOR_EXERCISE_TITLE = 'New Exercise Post';

export const NEW_POST_FOR_LECTURE_TITLE = 'New Lecture Post';

export const NEW_ANSWER_POST_FOR_EXERCISE_TITLE = 'New Exercise Reply';

export const NEW_ANSWER_POST_FOR_LECTURE_TITLE = 'New Lecture Reply';

export const COURSE_ARCHIVE_STARTED_TITLE = 'Course archival started';

export const COURSE_ARCHIVE_FINISHED_TITLE = 'Course archival finished';

export const COURSE_ARCHIVE_FAILED_TITLE = 'Course archival failed';

export const EXAM_ARCHIVE_STARTED_TITLE = 'Exam archival started';

export const EXAM_ARCHIVE_FINISHED_TITLE = 'Exam archival finished';

export const EXAM_ARCHIVE_FAILED_TITLE = 'Exam archival failed';

export const notificationTitles: Set<string> = new Set([
    ATTACHMENT_CHANGE_TITLE,
    EXERCISE_CREATED_TITLE,
    EXERCISE_PRACTICE_TITLE,
    QUIZ_EXERCISE_STARTED_TITLE,
    EXERCISE_UPDATED_TITLE,
    DUPLICATE_TEST_CASE_TITLE,
    ILLEGAL_SUBMISSION_TITLE,
    NEW_POST_FOR_EXERCISE_TITLE,
    NEW_POST_FOR_LECTURE_TITLE,
    NEW_POST_FOR_LECTURE_TITLE,
    NEW_ANSWER_POST_FOR_EXERCISE_TITLE,
    NEW_ANSWER_POST_FOR_LECTURE_TITLE,
    COURSE_ARCHIVE_STARTED_TITLE,
    COURSE_ARCHIVE_FINISHED_TITLE,
    COURSE_ARCHIVE_FAILED_TITLE,
    EXAM_ARCHIVE_STARTED_TITLE,
    EXAM_ARCHIVE_FINISHED_TITLE,
    EXAM_ARCHIVE_FAILED_TITLE,
]);
