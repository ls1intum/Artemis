import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

export class VideoUnit extends LectureUnit {
    public description?: string;
    // src link of a video
    public source?: string;

    constructor() {
        super(LectureUnitType.VIDEO);
    }
}
