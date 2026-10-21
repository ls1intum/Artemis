/**
 * Lifecycle status of the Security Framework sandbox for a single programming exercise, as shown to
 * instructors on the activation card. This enum is the single source of truth the whole card renders
 * from (tint, status pill, toggle position, sync line, warnings) - never a combination of loose
 * booleans - so the card can never show two contradictory states at once.
 *
 * - INACTIVE:   the tests repository contains no security policy; student code runs without a sandbox.
 * - GENERATING: transient. Activation or a version change is being committed to the tests repository.
 * - ACTIVE:     the tests repository contains the security policy and the tests enforce it. Java
 *               exercises start out ACTIVE, because the Java templates ship with Ares enabled.
 * - DELETING:   transient. The security policy is being removed from the tests repository.
 * - ERROR:      the last operation failed; the repository keeps its previous state.
 *
 * GENERATING, DELETING and ERROR only occur in edit mode (an existing exercise with a real repo). In
 * create mode there is no repository yet, so toggling only stages ACTIVE <-> INACTIVE locally until
 * the exercise is created.
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

/** One selectable Ares release in the "Framework Version" dropdown. */
export interface SecurityFrameworkVersionOption {
    version: string;
    label: string;
}

/**
 * Security Framework state of a single programming exercise, as returned by the server. The server
 * derives it from the exercise's tests repository; it keeps no state of its own.
 */
export interface SecurityFrameworkConfig {
    status: SecurityActivationStatus;
    /** Ares version pinned in the build file of the tests repository; absent if the repository does not use Ares. */
    frameworkVersion?: string;
    /** Short hash of the last commit that changed the security policy, if any. */
    lastCommitHash?: string;
    /** ISO timestamp of that commit, if any. */
    lastSyncedAt?: string;
    /** Human-readable reason for the ERROR status, if any (shown in the error line). */
    errorDetail?: string;
}

/**
 * The create-mode choice the parent form owns, so it is applied on save even if the card is not rendered at
 * that moment. Absent means "keep the template's setting" (active with the template's Ares version).
 */
export interface SecurityStagedChoice {
    active: boolean;
    /** The Ares version to pin; absent while the supported versions are unknown, in which case the template's version stays. */
    frameworkVersion?: string;
}

/** True when the sandbox is fully activated and enforcing (derived, never stored separately). */
export function isSecurityActive(config: SecurityFrameworkConfig): boolean {
    return config.status === SecurityActivationStatus.ACTIVE;
}
