import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission-exercise-type.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

export class FileUploadParticipation extends StudentParticipation {
    public isOwner?: boolean;
}

export class FileUploadSubmission extends Submission {
    public filePath?: string;
    public filePathUrl?: string;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
        this.filePathUrl = addPublicFilePrefix(this.filePath);
    }
}
