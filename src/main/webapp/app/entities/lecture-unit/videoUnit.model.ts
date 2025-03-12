import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';

export class VideoUnit extends LectureUnit {
    public description?: string;
    // src link of a video
    public source?: string;

    public correspondingAttachmentUnit?: AttachmentUnit;

    constructor() {
        super(LectureUnitType.VIDEO);
    }
}
