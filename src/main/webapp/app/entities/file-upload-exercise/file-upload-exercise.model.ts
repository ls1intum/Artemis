import { Exercise } from '../exercise';

export class FileUploadExercise extends Exercise {
    constructor(
        public id?: number,
        public filePattern?: string,
    ) {
        super();
    }
}
