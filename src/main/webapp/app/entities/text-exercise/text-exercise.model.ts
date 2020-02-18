import { Exercise, ExerciseType } from 'app/entities/exercise/exercise.model';
import { Course } from 'app/entities/course/course.model';

export class TextExercise extends Exercise {
    public sampleSolution: string;

    constructor(course?: Course) {
        super(ExerciseType.TEXT);
        this.course = course || null;
    }
}
