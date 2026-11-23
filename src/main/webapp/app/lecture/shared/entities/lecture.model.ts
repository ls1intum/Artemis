import { Dayjs } from 'dayjs/esm';
import { BaseEntity } from 'app/foundation/model/base-entity';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/course/shared/entities/course.model';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export class Lecture implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    startDate?: Dayjs;
    endDate?: Dayjs;
    posts?: Post[];
    lectureUnits?: LectureUnit[];
    course?: Course;
    isTutorialLecture?: boolean;

    // helper attribute
    channelName?: string;
    isAtLeastEditor?: boolean;
    isAtLeastInstructor?: boolean;
}
