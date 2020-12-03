/**
 * After automatic detection, each plagiarism has to be reviewed and revalidated by an instructor.
 */
export enum PlagiarismStatus {
    /**
     * Plagiarism has been confirmed by an instructor.
     */
    CONFIRMED = 'confirmed',

    /**
     * Plagiarism has been denied by an instructor.
     */
    DENIED = 'denied',

    /**
     * The incident has not been reviewed yet.
     */
    NONE = 'none',
}
