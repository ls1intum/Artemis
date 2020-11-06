import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Exercise } from 'app/entities/exercise.model';

export class ExerciseUnit extends LectureUnit {
    public exercise?: Exercise;

    constructor() {
        super(LectureUnitType.EXERCISE);
    }
}
