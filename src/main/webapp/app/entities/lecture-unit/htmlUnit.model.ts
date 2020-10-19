import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

export class HTMLUnit extends LectureUnit {
    public markdown?: string;

    constructor() {
        super(LectureUnitType.HTML);
    }
}
