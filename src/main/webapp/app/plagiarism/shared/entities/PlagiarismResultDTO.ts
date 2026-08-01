import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

/**
 * Result of the automatic plagiarism detection for exercises.
 */
export interface PlagiarismResultDTO {
    plagiarismResult: PlagiarismResult;
    plagiarismResultStats: PlagiarismResultStatsDTO;
}

export interface PlagiarismResultStatsDTO {
    numberOfDetectedSubmissions: number;
    averageSimilarity: number;
    maximalSimilarity: number;
    createdBy: string;
}
