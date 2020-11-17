import { PlagiarismSubmissionElement } from '../PlagiarismSubmissionElement';

export class TextSubmissionElement extends PlagiarismSubmissionElement {
    column: number;
    line: number;
    file: string;
    length: number;
}
