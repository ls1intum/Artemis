import { PlagiarismComparison } from './PlagiarismComparison';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';
import { Exercise } from 'app/entities/exercise.model';

/**
 * Base result of any automatic plagiarism detection.
 */
export abstract class PlagiarismResult<E extends PlagiarismSubmissionElement> {
    id?: number;

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    comparisons: PlagiarismComparison<E>[];

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    duration: number;

    /**
     * Exercise for which plagiarism was detected.
     */
    exercise: Exercise;

    /**
     * 10-element array representing the similarity distribution of the detected comparisons.
     *
     * Each entry represents the absolute frequency of comparisons whose similarity lies within the
     * respective interval.
     *
     * Intervals:
     * 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%]
     */
    similarityDistribution: [number, 10];
}
