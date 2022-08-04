import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
export class PlagiarismCaseInfo {
    public id: number;
    public verdict?: PlagiarismVerdict;
}
