import { Exercise, ExerciseType } from 'app/entities/exercise/exercise.model';
import { Course } from 'app/entities/course/course.model';

export class FileUploadExercise extends Exercise {
    public filePattern: string;
    public sampleSolution: string;

    constructor(course?: Course) {
        super(ExerciseType.FILE_UPLOAD);
        this.course = course || null;
    }
}
