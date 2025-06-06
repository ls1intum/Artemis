import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
export class PlagiarismCaseInfo {
    public id: number;
    public verdict?: PlagiarismVerdict;
    public createdByContinuousPlagiarismControl?: boolean;
}
