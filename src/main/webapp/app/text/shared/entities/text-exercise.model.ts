import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

export class TextExercise extends Exercise {
    public exampleSolution?: string;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.TEXT);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
        // Set a default value
        this.assessmentType = AssessmentType.MANUAL;
    }
}
