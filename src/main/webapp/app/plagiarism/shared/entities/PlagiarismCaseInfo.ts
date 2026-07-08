import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

/**
 * A DTO with a subset of Plagiarism Case fields for displaying relevant info to a student.
 */
export interface PlagiarismCaseInfo {
    id: number;
    verdict?: PlagiarismVerdict;
    createdByContinuousPlagiarismControl?: boolean;
}
