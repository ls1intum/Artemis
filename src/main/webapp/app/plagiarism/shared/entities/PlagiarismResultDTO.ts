import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';
import { PlagiarismSubmissionElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';

/**
 * Result of the automatic plagiarism detection for exercises.
 */
export class PlagiarismResultDTO<E extends PlagiarismResult<PlagiarismSubmissionElement>> {
    plagiarismResult: E;
    plagiarismResultStats: PlagiarismResultStats;
}

export class PlagiarismResultStats {
    numberOfDetectedSubmissions: number;
    averageSimilarity: number;
    maximalSimilarity: number;
    createdBy: string;
}
