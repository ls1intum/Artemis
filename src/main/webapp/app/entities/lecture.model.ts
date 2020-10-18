import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { StudentQuestion } from 'app/entities/student-question.model';
import { Course } from 'app/entities/course.model';
import { LectureModule } from 'app/entities/lecture-module/lectureModule.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: Moment;
    endDate?: Moment;
    attachments?: Attachment[];
    studentQuestions?: StudentQuestion[];
    lectureModules?: LectureModule[];
    course?: Course;

    // helper attribute
    isAtLeastInstructor?: boolean;

    constructor() {}
}
