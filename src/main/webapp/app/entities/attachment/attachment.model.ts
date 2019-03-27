import { Moment } from 'moment';
import { BaseEntity } from 'app/shared';
import { Exercise } from 'app/entities/exercise';
import { Lecture } from 'app/entities/lecture';

export const enum AttachmentType {
    FILE = 'FILE',
    URL = 'URL'
}

export class Attachment implements BaseEntity {
    id: number;
    name: string;
    link: string;
    releaseDate: Moment;
    attachmentType: AttachmentType;
    lecture: Lecture;
    exercise: Exercise;

    constructor() {}
}
