/**
 * Per-course configuration values that are loaded lazily on the server (see the server-side {@code CourseConfiguration}
 * entity). Currently holds the grade-relevance flag driving the data-privacy retention period and the retention hold
 * suspending that cleanup.
 */
export class CourseConfiguration {
    public id?: number;
    public gradeRelevant?: boolean;
    public dataRetentionHold?: boolean;
}
