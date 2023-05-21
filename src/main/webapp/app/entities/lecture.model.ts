import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { Channel } from 'app/entities/metis/conversation/channel.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    attachments?: Attachment[];
    posts?: Post[];

    channel?: Channel;
    lectureUnits?: LectureUnit[];
    course?: Course;
    channel: Channel;

    // helper attribute
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;

    constructor() {}
}
