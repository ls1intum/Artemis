import dayjs from 'dayjs/esm';
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
    public notificationDate?: dayjs.Dayjs;
    public target?: string;
    public author?: User;

    protected constructor(notificationType: NotificationType) {
        this.notificationType = notificationType;
    }
}

/**
 * Corresponds to the server-side NotificationTitleTypeConstants(.java) constant Strings
 */
export const EXERCISE_SUBMISSION_ASSESSED_TITLE = 'Exercise Submission Assessed';

export const ATTACHMENT_CHANGE_TITLE = 'Attachment updated';

export const EXERCISE_RELEASED_TITLE = 'Exercise released';

export const EXERCISE_PRACTICE_TITLE = 'Exercise open for practice';

export const QUIZ_EXERCISE_STARTED_TITLE = 'Quiz started';

export const EXERCISE_UPDATED_TITLE = 'Exercise updated';

export const DUPLICATE_TEST_CASE_TITLE = 'Duplicate test case was found.';

export const ILLEGAL_SUBMISSION_TITLE = 'Illegal submission of a student.';

export const NEW_EXERCISE_POST_TITLE = 'New exercise post';

export const NEW_LECTURE_POST_TITLE = 'New lecture post';

export const NEW_ANNOUNCEMENT_POST_TITLE = 'New announcement';

export const NEW_COURSE_POST_TITLE = 'New course-wide post';

export const NEW_REPLY_FOR_EXERCISE_POST_TITLE = 'New reply for exercise post';

export const NEW_REPLY_FOR_LECTURE_POST_TITLE = 'New reply for lecture post';

export const NEW_REPLY_FOR_COURSE_POST_TITLE = 'New reply for course-wide post';

export const FILE_SUBMISSION_SUCCESSFUL_TITLE = 'File submission successful';

export const COURSE_ARCHIVE_STARTED_TITLE = 'Course archival started';

export const COURSE_ARCHIVE_FINISHED_TITLE = 'Course archival finished';

export const COURSE_ARCHIVE_FAILED_TITLE = 'Course archival failed';

export const EXAM_ARCHIVE_STARTED_TITLE = 'Exam archival started';

export const EXAM_ARCHIVE_FINISHED_TITLE = 'Exam archival finished';

export const EXAM_ARCHIVE_FAILED_TITLE = 'Exam archival failed';

export const PROGRAMMING_TEST_CASES_CHANGED_TITLE = 'Test cases for programming exercise changed';

export const NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT_TITLE = 'New possible plagiarism case';

export const PLAGIARISM_CASE_FINAL_STATE_STUDENT_TITLE = 'Final state for plagiarism case';

export const TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE = 'You have been registered to a tutorial group';

export const TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE = 'You have been deregistered from a tutorial group';

export const TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE = 'A student has been registered to your tutorial group';

export const TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE = 'A student has been deregistered from your tutorial group';

export const TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE = 'Multiple students have been registered to your tutorial group';

export const TUTORIAL_GROUP_DELETED_TITLE = 'Tutorial Group deleted';

export const TUTORIAL_GROUP_UPDATED_TITLE = 'Tutorial Group updated';

export const TUTORIAL_GROUP_ASSIGNED_TITLE = 'You have been assigned to lead a tutorial group';

export const TUTORIAL_GROUP_UNASSIGNED_TITLE = 'You have been unassigned from leading a tutorial group';

// edge case: has no separate notificationType. Is created based on EXERCISE_UPDATED for exam exercises
export const LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE = 'Live Exam Exercise Update';
