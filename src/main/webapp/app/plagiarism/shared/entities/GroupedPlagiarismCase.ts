import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';

export interface GroupedPlagiarismCases {
    [exerciseId: number]: PlagiarismCase[];
}
