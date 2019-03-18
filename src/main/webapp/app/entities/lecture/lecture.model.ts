import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { Course } from 'app/entities/course';
import { Attachment } from 'app/entities/attachment/attachment.model';
import { StudentQuestion } from 'app/entities/student-question/student-question.model';

export class Lecture implements BaseEntity {
    id: number;
    title: string;
    description: string;
    startDate: Moment;
    endDate: Moment;
    attachments: Attachment[];
    studentQuestions: StudentQuestion[];
    course: Course;

    constructor() {}
}
