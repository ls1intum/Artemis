import { isDevMode } from '@angular/core';

/**
 * Semantic colour role, shared by every component that carries one.
 *
 * One union rather than one per component: a tag could not previously be given the same `severity` as the button
 * beside it, because `message` spelled the failure role `error` while `button` and `tag` spelled it `danger`.
 */
export type TumUiSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warning' | 'danger' | 'contrast';

/** Size step, shared by every component that has one. */
export type TumUiSize = 'small' | 'medium' | 'large';

/**
 * Lifecycle state of a unit of work. Deliberately not a {@link TumUiSeverity}: `running` and `queued` are not colour
 * roles, and a component that reports progress needs to tell "not started" from "could not be determined".
 */
export type TumUiRunState = 'queued' | 'running' | 'success' | 'warning' | 'danger' | 'neutral' | 'unknown';

/** Spellings that predate {@link TumUiSeverity}. Accepted for one minor, then removed. */
export type TumUiSeverityAlias = 'warn' | 'error';

/** Spellings that predate {@link TumUiRunState}. Accepted for one minor, then removed. */
export type TumUiRunStateAlias = 'error';

/** Spellings that predate {@link TumUiSize}. Accepted for one minor, then removed. */
export type TumUiSizeAlias = 'default' | 'normal';

const SEVERITY_ALIASES: Readonly<Record<TumUiSeverityAlias, TumUiSeverity>> = {
    warn: 'warning',
    error: 'danger',
};

const SIZE_ALIASES: Readonly<Record<TumUiSizeAlias, TumUiSize>> = {
    default: 'medium',
    normal: 'medium',
};

// One warning per distinct deprecation, not one per component instance: a table of fifty tags must not print fifty
// identical lines and bury everything else in the console.
const reportedDeprecations = new Set<string>();

function report(key: string, message: string): void {
    if (!isDevMode() || reportedDeprecations.has(key)) {
        return;
    }
    reportedDeprecations.add(key);
    window.console.warn(message);
}

/** Reports a deprecated input value once per distinct spelling, in development builds only. */
export function warnDeprecatedValue(component: string, input: string, used: string, replacement: string): void {
    report(`${component}.${input}=${used}`, `${component}: [${input}]="${used}" is deprecated and will be removed. Use "${replacement}".`);
}

/** Reports a deprecated input once per component and input, whatever value it was given. */
export function warnDeprecatedInput(component: string, input: string, replacement: string): void {
    report(`${component}.${input}`, `${component}: the [${input}] input is deprecated and will be removed. Use ${replacement}.`);
}

/** Maps a possibly-deprecated severity spelling onto the canonical union. */
export function resolveSeverity<T extends TumUiSeverity>(value: T | TumUiSeverityAlias, component: string): T {
    const alias = SEVERITY_ALIASES[value as TumUiSeverityAlias];
    if (alias) {
        warnDeprecatedValue(component, 'severity', value, alias);
        return alias as T;
    }
    return value as T;
}

/**
 * Maps a possibly-deprecated run-state spelling onto the canonical union.
 *
 * A run state is not a severity — `queued`, `running`, `neutral` and `unknown` are not colour roles — but the two
 * unions have to agree on the word for "this ended badly", or a consumer cannot hand the same string to a status dot
 * and to the message beside it. That word is `danger`; `error` is the old spelling.
 */
export function resolveRunState(value: TumUiRunState | TumUiRunStateAlias, component: string): TumUiRunState {
    if (value === 'error') {
        warnDeprecatedValue(component, 'state', value, 'danger');
        return 'danger';
    }
    return value;
}

/** Maps a possibly-deprecated or absent size spelling onto the canonical union. */
export function resolveSize(value: TumUiSize | TumUiSizeAlias | undefined, component: string, fallback: TumUiSize = 'medium'): TumUiSize {
    if (value === undefined) {
        return fallback;
    }
    const alias = SIZE_ALIASES[value as TumUiSizeAlias];
    if (alias) {
        warnDeprecatedValue(component, 'size', value, alias);
        return alias;
    }
    return value as TumUiSize;
}

/** Test seam: the deprecation log is de-duplicated process-wide, so a spec that asserts on it has to reset it. */
export function resetDeprecationWarningsForTesting(): void {
    reportedDeprecations.clear();
}
