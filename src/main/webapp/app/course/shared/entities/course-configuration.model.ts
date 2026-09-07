/**
 * Per-course configuration values that are loaded lazily on the server (see the server-side {@code CourseConfiguration}
 * entity). Holds the grade-relevance flag driving the data-privacy retention period, the retention hold suspending that
 * cleanup, and the per-course Atlas auto-orchestration settings.
 */
export class CourseConfiguration {
    public id?: number;
    public gradeRelevant?: boolean;
    public dataRetentionHold?: boolean;
    public autoOrchestratorEnabled?: boolean;
    public debounceWindowSecondsOverride?: number;
    public maxDailyOrchestrationOverride?: number;
}
