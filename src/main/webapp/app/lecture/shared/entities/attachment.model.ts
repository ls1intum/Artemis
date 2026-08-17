import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/foundation/model/base-entity';
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
    displayPageNumbers?: number[];
}

/**
 * Whether the attachment holds a PDF, judged by the name it is stored under: a student version replaces the link, and
 * an attachment that carries neither is still named after the file it holds.
 *
 * Everything deciding whether slides can be shown has to agree on this, or a unit renders a PDF that its surroundings
 * believe it does not have.
 */
export function attachmentIsPdf(attachment?: Attachment): boolean {
    const candidate = attachment?.studentVersion ?? attachment?.link ?? attachment?.name;
    return !!candidate?.toLowerCase().endsWith('.pdf');
}
