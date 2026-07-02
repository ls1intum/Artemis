import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';

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
export function isSubmissionPatchPayload(arg: unknown): arg is SubmissionPatchPayload {
    return typeof arg === 'object' && arg !== null && (arg as SubmissionPatchPayload).submissionPatch !== undefined && (arg as SubmissionPatchPayload).sender !== undefined;
}
