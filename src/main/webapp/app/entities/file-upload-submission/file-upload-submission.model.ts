import { Submission } from '../submission';

export class FileUploadSubmission extends Submission {
    constructor(
        public id?: number,
        public filePath?: string,
    ) {
        super();
    }
}
