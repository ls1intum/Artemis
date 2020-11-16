import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextElement } from 'app/exercises/shared/plagiarism/types/text/TextElement';

export class TextPlagiarismSubmission extends PlagiarismSubmission {
    elements: TextElement[];

    /**
     * List of files the related submission consists of.
     */
    files: string[];
}
