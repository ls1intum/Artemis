import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

export class TextExercise extends Exercise {
    public sampleSolution: string;

    constructor(course?: Course | null, exerciseGroup?: ExerciseGroup | null) {
        super(ExerciseType.TEXT);
        this.course = course || null;
        this.exerciseGroup = exerciseGroup || null;
    }
}
