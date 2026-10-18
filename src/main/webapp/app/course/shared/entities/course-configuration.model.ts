/**
 * Per-course configuration values that are loaded lazily on the server (see the server-side {@code CourseConfiguration}
 * entity). Holds the grade-relevance flag driving the data-privacy retention period, the retention hold suspending that
 * cleanup, the per-course Atlas auto-orchestration settings, and the conversion of feature bonus points into a bonus on the
 * final grade (both values unset while the course does not use the feature bonus).
 */
export class CourseConfiguration {
    public id?: number;
    public gradeRelevant?: boolean;
    public dataRetentionHold?: boolean;
    public autoOrchestratorEnabled?: boolean;
    public debounceWindowSecondsOverride?: number;
    public maxDailyOrchestrationOverride?: number;
    public featureBonusPercentPerPoint?: number;
    public featureBonusMaxPercent?: number;
}
