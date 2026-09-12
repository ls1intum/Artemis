export class SubmissionPatch {
    /**
     * Base64-encoded whole diagram
     */
    public patch: string;

    constructor(patch: string) {
        this.patch = patch;
    }
}
