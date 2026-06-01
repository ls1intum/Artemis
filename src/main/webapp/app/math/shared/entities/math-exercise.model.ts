import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';

export class MathExercise extends Exercise {
    public exampleSolution?: string;
    public description?: string;
    public manualDerivation?: boolean;

    constructor(course: Course | undefined) {
        super(ExerciseType.MATH);
        this.course = course;
    }
}
