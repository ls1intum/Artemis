/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class PlagiarismSubmissionElement {
    id!: number;
    column!: number;
    line!: number;
    file!: string;
    length!: number;
}

/** Instantiated via its constructor; fields are populated at construction time. */
export class FromToElement {
    from: PlagiarismSubmissionElement;
    to: PlagiarismSubmissionElement;

    constructor(from: PlagiarismSubmissionElement, to: PlagiarismSubmissionElement) {
        this.from = from;
        this.to = to;
    }
}
