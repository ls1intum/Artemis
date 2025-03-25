import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';

export class FileUploadExercise extends Exercise {
    public filePattern?: string;
    public exampleSolution?: string;

    constructor(course: Course | undefined, exerciseGroup: ExerciseGroup | undefined) {
        super(ExerciseType.FILE_UPLOAD);
        this.course = course;
        this.exerciseGroup = exerciseGroup;
    }
}
