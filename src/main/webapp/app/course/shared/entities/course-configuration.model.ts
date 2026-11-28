/** Who authors the first commit of the repositories of new programming exercises; mirrors the server enum. */
export enum InitialCommitAuthorMode {
    /** The user who created the exercise, which is what every course did before the setting existed. */
    EXERCISE_CREATOR = 'EXERCISE_CREATOR',
    /** The identity configured on the server as artemis.git.name and artemis.git.email. */
    PLACEHOLDER = 'PLACEHOLDER',
    /** A name and an email address the instructor entered. */
    CUSTOM = 'CUSTOM',
}

export interface InitialCommitAuthor {
    mode: InitialCommitAuthorMode;
    name?: string;
    email?: string;
}

/**
 * Per-course configuration values that are loaded lazily on the server (see the server-side {@code CourseConfiguration}
 * entity). Holds the grade-relevance flag driving the data-privacy retention period, the retention hold suspending that
 * cleanup, and the per-course Atlas auto-orchestration settings.
 */
export class CourseConfiguration {
    public id?: number;
    public studentWelcomeContext?: string;
    public gradeRelevant?: boolean;
    public dataRetentionHold?: boolean;
    public autoOrchestratorEnabled?: boolean;
    public debounceWindowSecondsOverride?: number;
    public maxDailyOrchestrationOverride?: number;
    public initialCommitAuthor?: InitialCommitAuthor;
}
