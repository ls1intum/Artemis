import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

export class VideoUnit extends LectureUnit {
    public description?: string;
    // id of a YouTube video
    public source?: string;

    constructor() {
        super(LectureUnitType.VIDEO);
    }
}
