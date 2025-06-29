import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for exercises.
 */
export class PlagiarismResultDTO {
    plagiarismResult: PlagiarismResult;
    plagiarismResultStats: PlagiarismResultStats;
}

export class PlagiarismResultStats {
    numberOfDetectedSubmissions: number;
    averageSimilarity: number;
    maximalSimilarity: number;
    createdBy: string;
}
