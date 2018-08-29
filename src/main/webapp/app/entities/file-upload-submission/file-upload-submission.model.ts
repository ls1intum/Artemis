import { Submission, SubmissionExerciseType } from '../submission';

export class FileUploadSubmission extends Submission {

    public filePath: string;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
    }
}
