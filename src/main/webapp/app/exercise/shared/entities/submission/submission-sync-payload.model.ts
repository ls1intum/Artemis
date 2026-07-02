import { User } from 'app/account/user/user.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';

export class SubmissionSyncPayload {
    public submission: Submission;
    public sender: User;
}

export function isSubmissionSyncPayload(arg: unknown): arg is SubmissionSyncPayload {
    return typeof arg === 'object' && arg !== null && (arg as SubmissionSyncPayload).submission !== undefined && (arg as SubmissionSyncPayload).sender !== undefined;
}
