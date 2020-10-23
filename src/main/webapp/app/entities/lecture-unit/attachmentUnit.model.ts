import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/entities/attachment.model';

export class AttachmentUnit extends LectureUnit {
    public description?: String;
    public attachment?: Attachment;

    constructor() {
        super(LectureUnitType.ATTACHMENT);
    }
}
