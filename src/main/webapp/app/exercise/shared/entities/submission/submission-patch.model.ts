import { Operation } from 'fast-json-patch';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

/**
 * A patch for a submission. It contains a list of operations that should be applied to the submission,
 * in the format of a JSON Patch (RFC 6902).
 */
export class SubmissionPatch /*implements BaseEntity*/ {
    /**
     * The participation the submission belongs to
     */
    public participation?: Participation;
    /**
     * The list of operations that should be applied to the submission,
     * in the format of a JSON Patch (RFC 6902)
     */
    public patch: Operation[];

    constructor(patch: Operation[]) {
        this.patch = patch;
    }
}
