import { TranslateService } from '@ngx-translate/core';

/** Returns a translated "Yes"/"No" label, or `undefined` when the value is not set. */
export function booleanLabel(translateService: TranslateService, value?: boolean): string | undefined {
    if (value === undefined) {
        return undefined;
    }
    return value ? translateService.instant('artemisApp.exercise.yes') : translateService.instant('artemisApp.exercise.no');
}

export type VersionHistoryViewMode = 'full' | 'changes';

/** Returns whether two snapshot values differ after stable normalization. */
export function valuesDiffer(current: unknown, previous: unknown): boolean {
    return stableStringify(current) !== stableStringify(previous);
}

/** Returns a stable JSON representation for change detection across snapshots. */
export function stableStringify(value: unknown): string {
    return JSON.stringify(normalizeForStableStringify(value));
}

function normalizeForStableStringify(value: unknown): unknown {
    if (value === null || typeof value !== 'object') {
        return value;
    }

    if (Array.isArray(value)) {
        return value.map((entry) => normalizeForStableStringify(entry)).sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)));
    }

    const normalizedObject: Record<string, unknown> = {};
    for (const key of Object.keys(value).sort()) {
        normalizedObject[key] = normalizeForStableStringify((value as Record<string, unknown>)[key]);
    }
    return normalizedObject;
}
