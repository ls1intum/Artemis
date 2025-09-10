import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export class TextUnit extends LectureUnit {
    public content?: string;

    constructor() {
        super(LectureUnitType.TEXT);
    }
}
