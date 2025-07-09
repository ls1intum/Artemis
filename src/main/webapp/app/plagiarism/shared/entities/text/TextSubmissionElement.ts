import { PlagiarismSubmissionElement } from '../PlagiarismSubmissionElement';

export class FromToElement {
    from: TextSubmissionElement;
    to: TextSubmissionElement;
    constructor(from: TextSubmissionElement, to: TextSubmissionElement) {
        this.from = from;
        this.to = to;
    }
}

export class TextSubmissionElement extends PlagiarismSubmissionElement {
    id: number;
    column: number;
    line: number;
    file: string;
    length: number;
}
