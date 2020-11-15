import { PlagiarismComparison } from '../PlagiarismComparison';
import { ModelingComparisonElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingComparisonElement';

/**
 * Pair of two similar modeling exercise submissions.
 */
export class ModelingComparison extends PlagiarismComparison {
    element1: ModelingComparisonElement;
    element2: ModelingComparisonElement;
}
