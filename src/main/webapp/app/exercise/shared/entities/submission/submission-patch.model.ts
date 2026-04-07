import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

export class SubmissionPatch {
    /**
     * The participation the submission belongs to
     */
    public participation?: Participation;

    /**
     * Base64-encoded whole diagram
     */
    public patch: string;

    constructor(patch: string) {
        this.patch = patch;
    }
}
