import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { Course } from 'app/entities/course.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: Moment;
    endDate?: Moment;
    attachments?: Attachment[];
    studentQuestions?: StudentQuestion[];
    course?: Course;

    // helper attribute
    isAtLeastInstructor?: boolean;

    constructor() {}
}
