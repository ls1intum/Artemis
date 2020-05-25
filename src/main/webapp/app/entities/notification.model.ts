import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';

export enum NotificationType {
    SYSTEM = 'system',
    CONNECTION = 'connection',
    SINGLE = 'single',
    SINGLE_NEW_ANSWER_FOR_EXERCISE = 'single-newAnswerForExercise',
    SINGLE_NEW_ANSWER_FOR_LECTURE = 'single-newAnswerForLecture',
    GROUP = 'group',
    GROUP_ATTACHMENT_UPDATED = 'group-attachmentUpdated',
    GROUP_EXERCISE_CREATED = 'group-exerciseCreated',
    GROUP_EXERCISE_PRACTICE = 'group-exercisePractice',
    GROUP_EXERCISE_STARTED = 'group-exerciseStarted',
    GROUP_EXERCISE_UPDATED = 'group-exerciseUpdated',
    GROUP_NEW_ANSWER_FOR_EXERCISE = 'group-newAnswerForExercise',
    GROUP_NEW_ANSWER_FOR_LECTURE = 'group-newAnswerForLecture',
    GROUP_NEW_QUESTION_FOR_EXERCISE = 'group-newQuestionForExercise',
    GROUP_NEW_QUESTION_FOR_LECTURE = 'group-newQuestionForLecture',
}

export class Notification implements BaseEntity {
    public id: number;
    public notificationType: NotificationType;
    public title: string;
    public text: string;
    public notificationDate: Moment | null;
    public target: string;
    public author: User;
    public course: Course;
}
