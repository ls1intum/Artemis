import { User } from 'app/core/user/user.model';
import { Submission } from 'app/exercise/entities/submission.model';

export class SubmissionSyncPayload {
    public submission: Submission;
    public sender: User;
}

export function isSubmissionSyncPayload(arg: any): arg is SubmissionSyncPayload {
    return arg.submission !== undefined && arg.sender !== undefined;
}
