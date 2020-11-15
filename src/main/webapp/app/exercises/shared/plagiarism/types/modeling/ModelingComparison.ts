import { ModelingSubmissionComparisonElement } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { PlagiarismComparison } from '../PlagiarismComparison';

/**
 * Pair of two similar modeling exercise submissions.
 */
export class ModelingComparison extends PlagiarismComparison {
    element1: ModelingSubmissionComparisonElement;
    element2: ModelingSubmissionComparisonElement;
}
