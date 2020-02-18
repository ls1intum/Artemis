import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment/attachment.model';
import { StudentQuestion } from 'app/entities/student-question/student-question.model';
import { Course } from 'app/entities/course/course.model';

export class Lecture implements BaseEntity {
    id: number;
    title: string;
    description: string;
    startDate: Moment | null;
    endDate: Moment | null;
    attachments: Attachment[];
    studentQuestions: StudentQuestion[];
    course: Course;

    // helper attribute
    isAtLeastInstructor: boolean;

    constructor() {}
}
