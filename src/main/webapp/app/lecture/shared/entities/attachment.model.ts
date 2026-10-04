import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/foundation/model/base-entity';
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
    attachmentVideoUnit?: AttachmentVideoUnit;
    studentVersion?: string;
    displayPageNumbers?: number[];
}
