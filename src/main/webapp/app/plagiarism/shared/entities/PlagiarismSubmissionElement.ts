export class PlagiarismSubmissionElement {
    id: number;
    column: number;
    line: number;
    file: string;
    length: number;
}

export class FromToElement {
    from: PlagiarismSubmissionElement;
    to: PlagiarismSubmissionElement;

    constructor(from: PlagiarismSubmissionElement, to: PlagiarismSubmissionElement) {
        this.from = from;
        this.to = to;
    }
}
