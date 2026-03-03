package de.tum.cit.aet.artemis.exercise.dto.synchronization;

/**
 * Server-side event types emitted for exercise editor synchronization.
 * <p>
 * The client defines additional event types (e.g. {@code PROBLEM_STATEMENT_SYNC_*})
 * for client-to-client communication over the same WebSocket topic. Those types are
 * relayed by the server without deserialization and do not need representation here.
 */
public enum ExerciseEditorSyncEventType {
    NEW_COMMIT_ALERT, NEW_EXERCISE_VERSION_ALERT
}
