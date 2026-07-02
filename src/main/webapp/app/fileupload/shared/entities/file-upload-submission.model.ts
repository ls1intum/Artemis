import { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { addPublicFilePrefix } from 'app/app.constants';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';

export interface FileUploadSubmissionInputDTO {
    id?: number;
    submitted: boolean;
    exerciseId?: number;
}

export interface FileUploadSubmissionDTO {
    id?: number;
    submitted?: boolean;
    submissionDate?: dayjs.Dayjs;
    type?: SubmissionType;
    exampleSubmission?: boolean;
    submissionExerciseType?: SubmissionExerciseType;
    durationInMinutes?: number;
    filePath?: string;
    participation?: StudentParticipation;
    results?: Result[];
}

export class FileUploadSubmission extends Submission {
    public filePath?: string;
    public filePathUrl?: string;

    constructor() {
        super(SubmissionExerciseType.FILE_UPLOAD);
        this.filePathUrl = addPublicFilePrefix(this.filePath);
    }
}
