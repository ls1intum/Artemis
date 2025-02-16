import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

export interface GroupedPlagiarismCases {
    [exerciseId: number]: PlagiarismCase[];
}
