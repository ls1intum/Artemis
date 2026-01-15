import { Dayjs } from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: Dayjs;
    endDate?: Dayjs;
    visibleDate?: Dayjs;
    attachments?: Attachment[];
    posts?: Post[];
    lectureUnits?: LectureUnit[];
    course?: Course;
    isTutorialLecture?: boolean;

    // helper attribute
    channelName?: string;
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;
}

export class LectureSeriesCreateLectureDTO {
    constructor(
        public title: string,
        public startDate?: Dayjs,
        public endDate?: Dayjs,
    ) {}
}

/**
 * DTO for creating and updating lectures. Matches the server-side SimpleLectureDTO.
 */
export interface SimpleLectureDTO {
    id?: number;
    title?: string;
    description?: string;
    startDate?: string;
    endDate?: string;
    isTutorialLecture?: boolean;
    channelName?: string;
    course?: { id?: number };
}
