import { Moment } from 'moment';
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
    releaseDate?: Moment;
    version?: number;
    uploadDate?: Moment;
    attachmentType?: AttachmentType;
    lecture?: Lecture;
    exercise?: Exercise;
    attachmentUnit?: AttachmentUnit;

    constructor() {}
}
