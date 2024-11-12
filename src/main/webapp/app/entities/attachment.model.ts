import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

export const enum AttachmentType {
    FILE = 'FILE',
    URL = 'URL',
}

export class Attachment implements BaseEntity {
    id?: number;
    name?: string;
    link?: string;
    releaseDate?: dayjs.Dayjs;
    version?: number;
    uploadDate?: dayjs.Dayjs;
    attachmentType?: AttachmentType;
    lecture?: Lecture;
    exercise?: Exercise;
    attachmentUnit?: AttachmentUnit;

    constructor() {}
}
