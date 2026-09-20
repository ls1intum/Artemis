/**
 * Lifecycle status of the Security Framework sandbox for a single programming exercise, as shown to
 * instructors on the activation card. This enum is the single source of truth the whole card renders
 * from (tint, status pill, toggle position, sync line, warnings) - never a combination of loose
 * booleans - so the card can never show two contradictory states at once.
 *
 * - INACTIVE:   not turned on for this exercise; student code runs without a sandbox.
 * - GENERATING: transient. Activation in progress - the policy is being generated and committed.
 * - ACTIVE:     on. The policy has been committed to the exercise-tests repository and is enforcing.
 * - DELETING:   transient. Deactivation in progress - the sandbox effects are being reverted.
 * - ERROR:      a generate / sync / delete operation failed; the sandbox is not enforcing.
 *
 * GENERATING, DELETING and ERROR only occur in edit mode (an existing exercise with a real repo). In
 * create mode there is no repository yet, so toggling only stages INACTIVE <-> ACTIVE locally until
 * the exercise is generated.
 */
export enum SecurityActivationStatus {
    INACTIVE = 'INACTIVE',
    GENERATING = 'GENERATING',
    ACTIVE = 'ACTIVE',
    DELETING = 'DELETING',
    ERROR = 'ERROR',
}

/** Statuses that represent an in-progress background operation (spinner + disabled controls). */
export const TRANSIENT_SECURITY_STATUSES: ReadonlySet<SecurityActivationStatus> = new Set([SecurityActivationStatus.GENERATING, SecurityActivationStatus.DELETING]);

/** One selectable framework release in the "Framework Version" dropdown. */
export interface SecurityFrameworkVersionOption {
    version: string;
    label: string;
    /** True for the newest release; the component appends a localized "(latest)" suffix to its label. */
    latest?: boolean;
}

/**
 * Security Framework activation state for a single programming exercise.
 *
 * This is the contract the mock data layer exposes today and the real backend endpoints
 * (server-side Objective 1) are expected to satisfy once they exist, so swapping the mock service
 * for an HttpClient-backed one should not require any change to the component that renders this.
 */
export interface SecurityFrameworkConfig {
    status: SecurityActivationStatus;
    frameworkVersion: string;
    /** Short hash of the last commit pushed to the exercise-tests repository, if any. */
    lastCommitHash?: string;
    /** ISO timestamp of the last successful sync, if any (drives the "Synced 4 min ago" line). */
    lastSyncedAt?: string;
    /** Human-readable reason for the ERROR status, if any (shown in the error line). */
    errorDetail?: string;
}

/** True when the sandbox is fully activated and enforcing (derived, never stored separately). */
export function isSecurityActive(config: SecurityFrameworkConfig): boolean {
    return config.status === SecurityActivationStatus.ACTIVE;
}
