import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { ModelingSubmissionComparisonElement } from 'app/exercises/modeling/manage/modeling-exercise.service';

export interface ModelingComparison extends PlagiarismComparison {
    element1: ModelingSubmissionComparisonElement;
    element2: ModelingSubmissionComparisonElement;
}
