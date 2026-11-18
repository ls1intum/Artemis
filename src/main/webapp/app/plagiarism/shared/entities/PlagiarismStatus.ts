/**
 * After automatic detection, each plagiarism has to be reviewed and revalidated by an instructor.
 */
export enum PlagiarismStatus {
    /**
     * Plagiarism has been confirmed by an instructor.
     */
    CONFIRMED = 'CONFIRMED',

    /**
     * Plagiarism has been denied by an instructor.
     */
    DENIED = 'DENIED',

    /**
     * The incident has not been reviewed yet.
     */
    NONE = 'NONE',
}
