import { Operation } from 'fast-json-patch';
import { BaseEntity } from 'app/shared/model/base-entity';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';

/**
 * A patch for a submission. It contains a list of operations that should be applied to the submission,
 * in the format of a JSON Patch (RFC 6902).
 */
export abstract class SubmissionPatch implements BaseEntity {
    /**
     * The unique identifier for the patch
     */
    public id?: number;
    /**
     * The type of the submission
     */
    public submissionExerciseType?: SubmissionExerciseType;
    /**
     * The participation the submission belongs to
     */
    public participation?: Participation;
    /**
     * The list of operations that should be applied to the submission,
     * in the format of a JSON Patch (RFC 6902)
     */
    public patch: Operation[];

    /**
     * Generate a unique identifier for the patch. Patches are relatively
     * short-lived, so the identifier does not require strict uniqueness.
     * This method generates Ids that are fast to produce and have a low
     * chance of collision for the lifetime of the patch.
     * @protected
     */
    protected static generateId(): number {
        return parseInt(Math.random().toFixed(16).substring(2));
    }

    protected constructor(patch: Operation[], submissionExerciseType: SubmissionExerciseType) {
        this.id = SubmissionPatch.generateId();
        this.patch = patch;
        this.submissionExerciseType = submissionExerciseType;
    }
}
