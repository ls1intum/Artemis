import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

export const enum AttachmentType {
    FILE = 'FILE',
    URL = 'URL',
}

export class Attachment implements BaseEntity {
    id?: number;
    name?: string;
    link?: string;
    linkUrl?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    uploadDate?: dayjs.Dayjs;
    attachmentType?: AttachmentType;
    lecture?: Lecture;
    exercise?: Exercise;
    attachmentVideoUnit?: AttachmentVideoUnit;
    studentVersion?: string;
}
