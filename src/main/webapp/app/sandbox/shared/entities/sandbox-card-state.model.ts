import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { SandboxConfiguration } from 'app/openapi/model/sandbox-configuration';
import { SandboxPolicyFileProblemEnum } from 'app/openapi/model/sandbox-policy-file';

/**
 * What the sandbox card shows: the settled states the server reports, plus the states that exist only in the client while a change is sent
 * (GENERATING while the policy is committed, DELETING while it is removed) and after it failed (ERROR).
 */
export enum SandboxCardStatus {
    INACTIVE = 'INACTIVE',
    GENERATING = 'GENERATING',
    CONFIGURED = 'CONFIGURED',
    DELETING = 'DELETING',
    ERROR = 'ERROR',
    MISCONFIGURED = 'MISCONFIGURED',
    UNSUPPORTED = 'UNSUPPORTED',
}

/** The states in which a change is in flight, so the controls stay locked. */
export const TRANSIENT_SANDBOX_STATUSES: ReadonlySet<SandboxCardStatus> = new Set([SandboxCardStatus.GENERATING, SandboxCardStatus.DELETING]);

/** Problems of a policy file that activating cannot repair, because the file exists but Ares cannot use it. */
export const UNREPAIRABLE_POLICY_FILE_PROBLEMS: ReadonlySet<SandboxPolicyFileProblemEnum> = new Set<SandboxPolicyFileProblemEnum>(['INVALID_PATH', 'TOO_LARGE', 'UNPARSEABLE']);

/** Problems of a policy file that no Ares version resolves, so picking another version cannot help. */
export const VERSION_INDEPENDENT_POLICY_FILE_PROBLEMS: ReadonlySet<SandboxPolicyFileProblemEnum> = new Set<SandboxPolicyFileProblemEnum>(['INVALID_PATH', 'TOO_LARGE']);

/** The configuration the card shows: the one the server reported, with the card's own status and the message of a change that failed. */
export interface SandboxCardState extends Omit<SandboxConfiguration, 'status'> {
    status: SandboxCardStatus;
    errorDetail?: string;
}

/** An entry of the version select. */
export interface SandboxVersionOption {
    version: string;
    label: string;
    /** The version does not accept the policy files, so it is listed for reference but cannot be chosen. */
    incompatible?: boolean;
}

/** A translatable message with its parameters. */
export interface SandboxMessage {
    key: string;
    params: Record<string, string>;
}

const TRANSLATION_PREFIX = 'artemisApp.sandbox';

/**
 * @param configuration the configuration the server reported
 * @returns the state the card shows for it
 */
export function toCardState(configuration: SandboxConfiguration): SandboxCardState {
    const { status, ...rest } = configuration;
    return cloneWith(rest, { status: (status ?? SandboxCardStatus.INACTIVE) as SandboxCardStatus });
}

/**
 * @param state the state of the card
 * @returns why the sandbox is MISCONFIGURED or UNSUPPORTED, one translatable message per broken policy file or reason; empty otherwise
 */
export function sandboxProblemMessages(state: SandboxCardState): SandboxMessage[] {
    if (state.status === SandboxCardStatus.UNSUPPORTED) {
        return (state.unsupportedReasons ?? []).map((reason) => ({
            key: `${TRANSLATION_PREFIX}.unsupportedReason.${reason.type}`,
            params: { location: reason.location ?? '' },
        }));
    }
    if (state.status === SandboxCardStatus.MISCONFIGURED) {
        return (state.policyFiles ?? [])
            .filter((policyFile) => policyFile.problem !== undefined && policyFile.problem !== 'TEST_CLASS_LIST_NOT_FOUND')
            .map((policyFile) => ({
                key: `${TRANSLATION_PREFIX}.misconfigured.${policyFile.problem}`,
                params: { path: policyFile.policyPath ?? '', classes: (policyFile.referencedBy ?? []).join(', ') },
            }));
    }
    return [];
}
