import { User } from 'app/core/user/user.model';
import { Submission } from 'app/entities/submission.model';

export class SubmissionSyncPayload {
    public submission: Submission;
    public sender: User;
}
