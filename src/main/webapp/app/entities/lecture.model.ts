import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/entities/attachment.model';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/core/shared/entities/course.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { IngestionState } from 'app/entities/lecture-unit/attachmentUnit.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: dayjs.Dayjs;
    endDate?: dayjs.Dayjs;
    visibleDate?: dayjs.Dayjs;
    attachments?: Attachment[];
    posts?: Post[];
    lectureUnits?: LectureUnit[];
    course?: Course;

    // helper attribute
    channelName?: string;
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;
    ingested?: IngestionState;
}
