import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

export class ProofExercise extends Exercise {
    public exampleSolution?: string;
    public description?: string;
    public predefinedCheckboxState?: boolean;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.PROOF);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
    }
}
