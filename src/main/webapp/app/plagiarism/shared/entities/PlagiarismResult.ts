import { PlagiarismComparison } from './PlagiarismComparison';
import dayjs from 'dayjs/esm';

/**
 * Base result of any automatic plagiarism detection.
 */
export class PlagiarismResult {
    id?: number;

    /**
     * List of detected comparisons whose similarity is above the specified threshold.
     */
    comparisons: PlagiarismComparison[];

    /**
     * Duration of the plagiarism detection run in milliseconds.
     */
    duration: number;

    /**
     * 10-element array representing the similarity distribution of the detected comparisons.
     *
     * Each entry represents the absolute frequency of comparisons whose similarity lies within the
     * respective interval.
     *
     * Intervals:
     * 0: [0% - 10%), 1: [10% - 20%), 2: [20% - 30%), ..., 9: [90% - 100%]
     */
    similarityDistribution: number[];

    /**
     * Time when the plagiarism checks started.
     */
    createdDate: dayjs.Dayjs;
}
