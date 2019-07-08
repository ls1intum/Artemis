import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';

export class TextExercise extends Exercise {
    public sampleSolution: string;
    public automaticAssessmentEnabled = false;

    constructor(course?: Course) {
        super(ExerciseType.TEXT);
        this.course = course || null;
    }
}
