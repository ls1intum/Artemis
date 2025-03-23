import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { Exercise } from 'app/exercise/entities/exercise.model';

export class ExerciseUnit extends LectureUnit {
    public exercise?: Exercise;

    constructor() {
        super(LectureUnitType.EXERCISE);
    }
}
