import { Exercise, ExerciseType } from '../exercise';
import { Course } from '../course';

export class FileUploadExercise extends Exercise {

    public filePattern: string;

    constructor(course?: Course) {
        super(ExerciseType.FILE_UPLOAD);
        this.course = course;
    }
}
