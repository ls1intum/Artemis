import { User } from 'app/core/user/user.model';
import { Submission } from 'app/entities/submission.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';

export class SubmissionSyncPayload {
    public submission: Submission;
    public sender: User;
}

export class TextSubmissionSyncPayload extends SubmissionSyncPayload {
    public submission: TextSubmission;
}

export class ModelingSubmissionSyncPayload extends SubmissionSyncPayload {
    public submission: ModelingSubmission;
}
