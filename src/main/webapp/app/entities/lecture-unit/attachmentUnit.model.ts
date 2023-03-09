import { Attachment } from 'app/entities/attachment.model';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

export class AttachmentUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;

    constructor() {
        super(LectureUnitType.ATTACHMENT);
    }
}
