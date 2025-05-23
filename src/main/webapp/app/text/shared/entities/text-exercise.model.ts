import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

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
