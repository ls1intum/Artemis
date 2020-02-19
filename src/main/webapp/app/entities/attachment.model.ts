import { Moment } from 'moment';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Lecture } from 'app/entities/lecture.model';
import { Exercise } from 'app/entities/exercise.model';

export const enum AttachmentType {
    FILE = 'FILE',
    URL = 'URL',
}

export class Attachment implements BaseEntity {
    id: number;
    name: string;
    link: string | null;
    releaseDate: Moment | null;
    version: number;
    uploadDate: Moment | null;
    attachmentType: AttachmentType;
    lecture: Lecture;
    exercise: Exercise;

    constructor() {}
}
