/**
 * Each `PlagiarismComparisonElement` refers to a submission that has been compared during plagiarism detection.
 * It contains fundamental information independent of the exercise type or algorithm used.
 */
export class PlagiarismComparisonElement {
    /**
     * Login of the student who created the submission.
     */
    studentLogin: string;

    /**
     * ID of the compared submission.
     */
    submissionId: number;
}
