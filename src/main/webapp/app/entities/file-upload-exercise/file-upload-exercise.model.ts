import { Exercise, ExerciseType } from '../exercise';

export class FileUploadExercise extends Exercise {

    public filePattern: string;

    constructor() {
        super(ExerciseType.FILE_UPLOAD);
    }
}
