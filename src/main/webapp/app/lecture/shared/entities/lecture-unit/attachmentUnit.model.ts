import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { Slide } from 'app/lecture/shared/entities/lecture-unit/slide.model';

export class AttachmentUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;
    public slides?: Slide[];
    public pyrisIngestionState?: IngestionState;

    constructor() {
        super(LectureUnitType.ATTACHMENT);
    }
}

export enum IngestionState {
    NOT_STARTED = 'NOT_STARTED',
    IN_PROGRESS = 'IN_PROGRESS',
    DONE = 'DONE',
    ERROR = 'ERROR',
    PARTIALLY_INGESTED = 'PARTIALLY_INGESTED',
}
