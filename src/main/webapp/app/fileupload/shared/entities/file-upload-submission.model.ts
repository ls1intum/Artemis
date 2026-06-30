import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';

export class FileUploadSubmission extends Submission {
    public filePath?: string;
    public filePathUrl?: string;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
        this.filePathUrl = addPublicFilePrefix(this.filePath);
    }
}
