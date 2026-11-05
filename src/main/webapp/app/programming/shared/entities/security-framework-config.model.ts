import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * Lifecycle status of the Security Framework sandbox for a single programming exercise, as shown to
 * instructors on the activation card. This enum is the single source of truth the whole card renders
 * from (tint, status pill, toggle position, sync line, warnings) - never a combination of loose
 * booleans - so the card can never show two contradictory states at once.
 *
 * Settled states, derived by the server from the tests repository:
 * - INACTIVE:      every test class has switched its policy off; student code runs without a sandbox.
 * - ACTIVE:        at least one test class enforces a policy. Java exercises start out ACTIVE, because the
 *                  Java templates ship with Ares enabled.
 * - MISCONFIGURED: an active policy points to a policy file that is missing, invalid or unreadable, so Ares
 *                  fails the tests.
 * - UNSUPPORTED:   the tests use Ares in a way Artemis cannot manage automatically, e.g. Ares 1 or no Ares.
 *
 * Client-only states:
 * - GENERATING: transient. Activation, a repair or a version change is being committed to the tests repository.
 * - DELETING:   transient. The security policy is being removed from the tests repository.
 * - ERROR:      the last operation failed; the repository keeps its previous state.
 *
 * GENERATING, DELETING and ERROR only occur in edit mode (an existing exercise with a real repository). In
 * create and import mode, changes are only staged and sent with the create or import request.
 */
export enum SecurityActivationStatus {
    INACTIVE = 'INACTIVE',
    GENERATING = 'GENERATING',
    ACTIVE = 'ACTIVE',
    DELETING = 'DELETING',
    ERROR = 'ERROR',
    MISCONFIGURED = 'MISCONFIGURED',
    UNSUPPORTED = 'UNSUPPORTED',
}

/** Statuses that represent an in-progress background operation (spinner + disabled controls). */
export const TRANSIENT_SECURITY_STATUSES: ReadonlySet<SecurityActivationStatus> = new Set([SecurityActivationStatus.GENERATING, SecurityActivationStatus.DELETING]);

/** Why a policy file cannot be used or checked, mirroring the server's {@code SecurityFrameworkState.PolicyFileProblem}. */
export enum SecurityPolicyFileProblem {
    INVALID_PATH = 'INVALID_PATH',
    MISSING = 'MISSING',
    TOO_LARGE = 'TOO_LARGE',
    UNPARSEABLE = 'UNPARSEABLE',
    TEST_CLASS_LIST_NOT_FOUND = 'TEST_CLASS_LIST_NOT_FOUND',
}

/** Policy file problems that Artemis cannot repair by activating, because the file exists but cannot be used. */
export const UNREPAIRABLE_POLICY_FILE_PROBLEMS: ReadonlySet<SecurityPolicyFileProblem> = new Set([
    SecurityPolicyFileProblem.INVALID_PATH,
    SecurityPolicyFileProblem.TOO_LARGE,
    SecurityPolicyFileProblem.UNPARSEABLE,
]);

/** Why Artemis cannot manage the sandbox of a tests repository, mirroring the server's {@code SecurityFrameworkState.UnsupportedReasonType}. */
export enum SecurityUnsupportedReasonType {
    UNEVALUABLE_ARGUMENTS = 'UNEVALUABLE_ARGUMENTS',
    POLICY_NOT_ON_TOP_LEVEL_TYPE = 'POLICY_NOT_ON_TOP_LEVEL_TYPE',
    POLICY_ON_ANNOTATION_TYPE = 'POLICY_ON_ANNOTATION_TYPE',
    MULTIPLE_TOP_LEVEL_TYPES = 'MULTIPLE_TOP_LEVEL_TYPES',
    TESTS_IN_NESTED_TYPE = 'TESTS_IN_NESTED_TYPE',
    TEST_CLASS_WITHOUT_POLICY = 'TEST_CLASS_WITHOUT_POLICY',
    TEST_CLASS_WITHOUT_ARES = 'TEST_CLASS_WITHOUT_ARES',
    ARES_1 = 'ARES_1',
    NO_ARES_2 = 'NO_ARES_2',
    NO_ARES_VERSION_PINNED = 'NO_ARES_VERSION_PINNED',
}

/** A policy file that an active policy points to. */
export interface SecurityPolicyFile {
    /** The path relative to the repository root. */
    policyPath: string;
    /** The test classes that point to it. */
    referencedBy?: string[];
    /** Why Ares cannot use it or why it cannot be checked; absent if it is fine. */
    problem?: SecurityPolicyFileProblem;
    /** The test classes that point to it but are not listed in it, so Ares treats them like student code. */
    missingTestClasses?: string[];
}

/** A reason why Artemis cannot manage the sandbox of a tests repository. */
export interface SecurityUnsupportedReason {
    /** The affected file, class or build file. */
    location: string;
    type: SecurityUnsupportedReasonType;
}

/** The Ares versions the server offers, mirroring the server's {@code SecurityFrameworkVersionsDTO}. */
export interface SecurityFrameworkVersions {
    /** The versions an instructor can pin, newest first. */
    selectableVersions: string[];
    /** The version the tests repository template of each project type pins, i.e. the version a new exercise starts with. Not filtered, so it may be no longer selectable. */
    templateVersions?: Partial<Record<ProjectType, string>>;
}

/** One selectable Ares release in the "Framework Version" dropdown. */
export interface SecurityFrameworkVersionOption {
    version: string;
    label: string;
}

/**
 * Security Framework state of a single programming exercise, as returned by the server. The server
 * derives it from the exercise's tests repository; it keeps no state of its own. Empty lists are omitted.
 */
export interface SecurityFrameworkConfig {
    status: SecurityActivationStatus;
    /** Ares version pinned in the build file of the tests repository; absent if the repository does not pin one. */
    frameworkVersion?: string;
    /** The test classes whose policy is switched off. */
    disabledTestClasses?: string[];
    /** The policy files that active policies point to, with their problems and the test classes they do not list. */
    policyFiles?: SecurityPolicyFile[];
    /** Why the repository cannot be managed automatically; only for UNSUPPORTED. */
    unsupportedReasons?: SecurityUnsupportedReason[];
    /** Human-readable reason for the client-only ERROR status, if any (shown in the error line). */
    errorDetail?: string;
}

/** What a choice made while creating or importing an exercise does with the sandbox, mirroring the server's {@code SecurityFrameworkChoiceDTO.Action}. */
export enum SecurityFrameworkAction {
    ACTIVATE = 'ACTIVATE',
    DEACTIVATE = 'DEACTIVATE',
}

/**
 * The Security Framework setting sent with a create or import request, mirroring the server's {@code SecurityFrameworkChoiceDTO}. The server applies it
 * before the first builds. Absent (undefined) keeps the template's or the imported setting.
 */
export interface SecurityFrameworkChoice {
    action: SecurityFrameworkAction;
    /** The Ares version to pin; absent to keep the version the repository pins. */
    frameworkVersion?: string;
}

/** Attaches the choice to the body of a create or import request as its `securityFramework` field; the body stays unchanged if there is no choice. */
export function withSecurityFrameworkChoice<T extends object>(body: T, choice: SecurityFrameworkChoice | undefined): T {
    return choice ? cloneWith(body, { securityFramework: choice }) : body;
}

/** Which form the card is part of: creating, editing or importing an exercise. Only `edit` changes a repository directly. */
export type SecurityCardMode = 'create' | 'edit' | 'import';

/** True when the sandbox is fully activated and enforcing (derived, never stored separately). */
export function isSecurityActive(config: SecurityFrameworkConfig): boolean {
    return config.status === SecurityActivationStatus.ACTIVE;
}

/** Whether two choices would send the same request. */
export function isSameSecurityChoice(first: SecurityFrameworkChoice | undefined, second: SecurityFrameworkChoice | undefined): boolean {
    return first?.action === second?.action && first?.frameworkVersion === second?.frameworkVersion;
}

/** A translatable message: a translation key and its parameters. */
export interface SecurityFrameworkMessage {
    key: string;
    params: Record<string, string>;
}

const SECURITY_TRANSLATION_PREFIX = 'artemisApp.programmingExercise.security';

/**
 * The messages that explain why a config is MISCONFIGURED or UNSUPPORTED: one per broken policy file, or one per reason Artemis cannot manage the
 * repository. Empty for every other state. Used by the card and by the warning the form shows when a choice was not applied.
 */
export function securityFrameworkProblemMessages(config: SecurityFrameworkConfig): SecurityFrameworkMessage[] {
    if (config.status === SecurityActivationStatus.UNSUPPORTED) {
        return (config.unsupportedReasons ?? []).map((reason) => ({
            key: `${SECURITY_TRANSLATION_PREFIX}.unsupportedReason.${reason.type}`,
            params: { location: reason.location },
        }));
    }
    if (config.status === SecurityActivationStatus.MISCONFIGURED) {
        return (config.policyFiles ?? [])
            .filter((policyFile) => policyFile.problem !== undefined && policyFile.problem !== SecurityPolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND)
            .map((policyFile) => ({
                key: `${SECURITY_TRANSLATION_PREFIX}.misconfigured.${policyFile.problem}`,
                params: { path: policyFile.policyPath, classes: (policyFile.referencedBy ?? []).join(', ') },
            }));
    }
    return [];
}
