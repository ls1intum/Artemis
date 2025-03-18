import { PlagiarismCase } from 'app/plagiarism/shared/types/PlagiarismCase';

export interface GroupedPlagiarismCases {
    [exerciseId: number]: PlagiarismCase[];
}
