import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Attachment } from 'app/entities/attachment.model';
import { Slide } from 'app/entities/lecture-unit/slide.model';

export class AttachmentUnit extends LectureUnit {
    public description?: string;
    public attachment?: Attachment;
    public slides?: Slide[];

    constructor() {
        super(LectureUnitType.ATTACHMENT);
    }
}
