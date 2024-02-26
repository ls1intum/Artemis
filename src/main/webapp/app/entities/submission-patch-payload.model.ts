import { SubmissionPatch } from 'app/entities/submission-patch.model';
import { User } from 'app/core/user/user.model';

export class SubmissionPatchPayload {
    public submissionPatch: SubmissionPatch;
    public sender: User;
}

export function isSubmissionPatchPayload(arg: any): arg is SubmissionPatchPayload {
    return arg.submissionPatch !== undefined && arg.sender !== undefined;
}
