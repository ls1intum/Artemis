/** The namespace a tracked feature belongs to. Mirrors the server-side {@code FeatureKind} enum. */
export enum FeatureKind {
    REST = 'REST',
    GIT = 'GIT',
    BACKGROUND = 'BACKGROUND',
}

/** One feature and what it was used for over the selected window. */
export interface FeatureUsageEntry {
    featureId: number;
    featureKind: FeatureKind;
    module: string;
    identifier: string;
    /** Absent when the endpoint carries no `@FeatureUsage` label. */
    featureLabel?: string;
    callCount: number;
    errorCount: number;
    durationSumMs: number;
    durationMaxMs: number;
    activeDays: number;
    /** Absent when the feature saw no usage in the window. */
    lastUsedDay?: string;
    /** The last time a server reported that this feature still exists. */
    lastRegisteredAt?: string;
    /** True when this Artemis version no longer offers the feature, so its zero usage needs no decision. */
    retired?: boolean;
}

/** Calls over the window from callers of one role. */
export interface FeatureUsageRoleShare {
    callerRole: string;
    callCount: number;
}

/** The whole report for one window. */
export interface FeatureUsageOverview {
    days: number;
    from: string;
    /** The role the report was filtered to, absent when it covers every caller. */
    callerRole?: string;
    trackedFeatures: number;
    /** Features still offered by this version that saw no usage. Retired ones are deliberately not counted. */
    unusedFeatures: number;
    /** Inventory entries this version no longer offers at all. */
    retiredFeatures: number;
    totalCalls: number;
    inventoryRefreshedAt?: string;
    /** When this deployment started recording, so the report cannot imply more evidence than it has. */
    recordingSince?: string;
    features?: FeatureUsageEntry[];
    /** Always covers every caller, so it stays comparable when a role filter is active. */
    roleDistribution?: FeatureUsageRoleShare[];
    /**
     * The exact distinct-day count per logical feature, keyed the same way the table groups its rows.
     * Absent when nothing was used in the window.
     */
    activeDaysPerFeature?: FeatureUsageActiveDays[];
}

/**
 * The number of distinct days one logical feature was used on.
 *
 * Computed server-side because the per-endpoint counts cannot be combined: summing double counts a day two endpoints
 * behind one label were both used on, and taking the largest misses the days only one of them was used on.
 */
export interface FeatureUsageActiveDays {
    module: string;
    /** The feature label when it has one, otherwise the endpoint identifier. */
    featureKey: string;
    activeDays: number;
}

/** One day of one feature's usage. */
export interface FeatureUsageTrendPoint {
    usageDay: string;
    callCount: number;
}

/** How many entities have one optional feature switched on. */
export interface FeatureAdoption {
    module: string;
    key: string;
    count: number;
    total: number;
}

/**
 * A table row. Several endpoints that share a `@FeatureUsage` label collapse into one row, which is what the label is
 * for, so a row is not necessarily a single endpoint.
 */
export interface FeatureUsageRow {
    key: string;
    module: string;
    /** The area within the module, from the curated catalogue. `other` for endpoints that are not catalogued yet. */
    area: string;
    /** The feature within the area, or the raw endpoint identifier when it is not catalogued. */
    feature: string;
    /** `area/feature`, what the flat table shows. */
    name: string;
    featureKind: FeatureKind;
    /** How many inventory entries this row aggregates. Greater than one only for labelled features. */
    endpointCount: number;
    /** The identifiers behind the row, so a label can be traced back to the endpoints it covers. */
    identifiers: string[];
    /** True only when every endpoint behind the row is gone from this version. */
    retired: boolean;
    /** Every inventory row behind this feature, so the trend chart covers the whole feature and not one of its endpoints. */
    featureIds: number[];
    callCount: number;
    errorCount: number;
    errorRate: number;
    /** Kept alongside the mean so the tree can compute a call-weighted mean for an area or a module. */
    durationSumMs: number;
    meanDurationMs: number;
    maxDurationMs: number;
    activeDays: number;
    lastUsedDay?: string;
}

/** One row of the explorable tree: a module, an area within it, or a single feature. */
export interface FeatureTreeNode {
    key: string;
    name: string;
    level: number;
    callCount: number;
    errorCount: number;
    errorRate: number;
    durationSumMs: number;
    /** Features below this node that this version still offers. */
    featureCount: number;
    /** Of those, how many saw no usage. The reason to drill into a quiet branch. */
    unusedCount: number;
    lastUsedDay?: string;
    children: FeatureTreeNode[];
}

/** A tree node flattened for rendering, carrying only what the row needs to draw itself. */
export interface FeatureTreeRow extends FeatureTreeNode {
    hasChildren: boolean;
    expanded: boolean;
    /** Share of the whole report's calls, so a module can be compared against its siblings at a glance. */
    sharePercent: number;
}

/** The area a feature falls into when the catalogue has no entry for its controller yet. */
export const UNCATALOGUED_AREA = 'other';

/** The windows the admin page offers. Must match the server-side allow list. */
export const FEATURE_USAGE_WINDOWS_IN_DAYS = [7, 30, 90, 180] as const;

/**
 * Caller roles offered as a filter, highest first. These are global authorities, so a user who instructs any course counts
 * as an instructor everywhere.
 */
export const FEATURE_USAGE_CALLER_ROLES = ['SUPER_ADMIN', 'ADMIN', 'INSTRUCTOR', 'EDITOR', 'TEACHING_ASSISTANT', 'STUDENT', 'ANONYMOUS'] as const;
