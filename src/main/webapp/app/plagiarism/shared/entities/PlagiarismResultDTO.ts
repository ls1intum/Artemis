import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for exercises.
 */
export class PlagiarismResultDTO {
    plagiarismResult: PlagiarismResult;
    plagiarismResultStats: PlagiarismResultStatsDTO;
}

export class PlagiarismResultStatsDTO {
    numberOfDetectedSubmissions: number;
    averageSimilarity: number;
    maximalSimilarity: number;
    createdBy: string;
}
