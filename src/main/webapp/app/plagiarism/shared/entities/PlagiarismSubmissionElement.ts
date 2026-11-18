/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class PlagiarismSubmissionElement {
    id!: number;
    column!: number;
    line!: number;
    file!: string;
    /**
     * Where the token ends, counted in characters from its start and without line breaks, so it only gives the end
     * column for a token that stays on one line. Superseded by `endLine` / `endColumn`; still sent because results
     * computed before those were recorded have nothing else.
     */
    length!: number;
    /** Where the token ends, as reported by JPlag. Undefined for a result computed before this was recorded. */
    endLine?: number;
    endColumn?: number;
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
