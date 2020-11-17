import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';

export class TextPlagiarismSubmission extends PlagiarismSubmission {
    elements: TextSubmissionElement[];

    /**
     * List of files the related submission consists of.
     */
    files: string[];
}
