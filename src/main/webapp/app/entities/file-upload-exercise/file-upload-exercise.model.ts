import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';

export class FileUploadExercise extends Exercise {
    public filePattern: string;
    public sampleSolution: string;

    constructor(course?: Course) {
        super(ExerciseType.FILE_UPLOAD);
        this.course = course || null;
    }
}
