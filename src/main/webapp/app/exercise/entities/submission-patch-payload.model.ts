import { SubmissionPatch } from 'app/exercise/entities/submission-patch.model';

/**
 * A payload for a submission patch. It contains the patch and the sender of the patch.
 */
export class SubmissionPatchPayload {
    public submissionPatch: SubmissionPatch;
    public sender: string;
}

/**
 * Type guard for the SubmissionPatchPayload
 * @param arg
 */
export function isSubmissionPatchPayload(arg: any): arg is SubmissionPatchPayload {
    return arg.submissionPatch !== undefined && arg.sender !== undefined;
}
