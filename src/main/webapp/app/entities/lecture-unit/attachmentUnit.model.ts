import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/entities/attachment.model';

export class AttachmentUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;

    public startPage?: string;
    public endPage?: string;

    constructor() {
        super(LectureUnitType.ATTACHMENT);
    }
}
