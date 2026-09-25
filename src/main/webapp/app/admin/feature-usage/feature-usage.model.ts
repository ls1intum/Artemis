/** The namespace an inventory entry belongs to. Mirrors the server-side {@code FeatureKind} enum. */
export enum FeatureKind {
    REST = 'REST',
    GIT = 'GIT',
    BACKGROUND = 'BACKGROUND',
}

/**
 * What a call says about use. Mirrors the server-side {@code FeatureInteraction} enum.
 *
 * Only actions and views are use of a feature. Automatic calls are made by the client on its own, such as a status probe
 * on every page load, and system calls by another system, so neither says that anybody used the feature.
 */
export enum FeatureInteraction {
    ACTION = 'ACTION',
    VIEW = 'VIEW',
    AUTOMATIC = 'AUTOMATIC',
    SYSTEM = 'SYSTEM',
}

/** What the usage of one feature amounts to. Mirrors the server-side {@code FeatureUsageStatus} enum. */
export enum FeatureUsageStatus {
    USED = 'USED',
    ONLY_AUTOMATIC = 'ONLY_AUTOMATIC',
    UNUSED = 'UNUSED',
    NOT_AVAILABLE = 'NOT_AVAILABLE',
}

/** One inventory row, that is one endpoint, git operation or background feature, over the selected window. */
export interface FeatureUsageEndpoint {
    featureId: number;
    featureKind: FeatureKind;
    module: string;
    identifier: string;
    /** The name of the catalogue feature it serves. A row no longer offered can keep a label no feature resolves any more. */
    featureLabel?: string;
    interaction: FeatureInteraction;
    /** The controller serving it, absent for git and background features. */
    resource?: string;
    callCount: number;
    errorCount: number;
    durationSumMs: number;
    durationMaxMs: number;
    activeDays: number;
    /** Absent when it saw no call in the window. */
    lastUsedDay?: string;
    lastRegisteredAt?: string;
    /** True when this Artemis version no longer offers it, so its zero usage needs no decision. */
    retired?: boolean;
}

/** One user-facing feature, summed over every endpoint and module that serves it. */
export interface UserFeatureUsage {
    /** The catalogue constant, for example `PROGRAMMING_ONLINE_EDITOR`; the name and description are translated. */
    feature: string;
    area: string;
    status: FeatureUsageStatus;
    /** Viewed, but never acted on, although the feature has endpoints that act. */
    noActions: boolean;
    actionCount: number;
    viewCount: number;
    automaticCount: number;
    systemCount: number;
    /** Failed actions and views; automatic calls are not use, so neither are their failures. */
    errorCount: number;
    durationSumMs: number;
    durationMaxMs: number;
    activeDays: number;
    actionDays: number;
    lastUsedDay?: string;
    lastActionDay?: string;
    modules?: string[];
    endpointCount: number;
    hasActionEndpoints: boolean;
}

/** Actions and views over the window from callers of one role. */
export interface FeatureUsageRoleShare {
    callerRole: string;
    callCount: number;
}

/** The whole report for one window. Every headline count is per feature, the unit the page leads with. */
export interface FeatureUsageOverview {
    days: number;
    from: string;
    /** The role the report was filtered to, absent when it covers every caller. */
    callerRole?: string;
    availableFeatures: number;
    usedFeatures: number;
    onlyAutomatic: number;
    unusedFeatures: number;
    notAvailable: number;
    noActions: number;
    retiredEndpoints: number;
    actionCount: number;
    viewCount: number;
    automaticCount: number;
    systemCount: number;
    inventoryRefreshedAt?: string;
    /** When this deployment started recording, so the report cannot imply more evidence than it has. */
    recordingSince?: string;
    /** One entry per catalogue feature, in catalogue order. */
    features?: UserFeatureUsage[];
    /** One entry per inventory row, busiest first. */
    endpoints?: FeatureUsageEndpoint[];
    /** Always covers every caller, so it stays comparable when a role filter is active. */
    roleDistribution?: FeatureUsageRoleShare[];
}

/** One day of calls of one interaction. Days without calls are absent. */
export interface FeatureUsageTrendPoint {
    usageDay: string;
    interaction: FeatureInteraction;
    callCount: number;
}

/** How many entities have one optional setting switched on, and which feature it switches on. */
export interface FeatureAdoption {
    module: string;
    key: string;
    feature?: string;
    count: number;
    total: number;
}

/** A row of the feature tree: a product area, a feature, the module and resource behind it, or a single endpoint. */
export interface FeatureTreeRow {
    key: string;
    level: 0 | 1 | 2 | 3;
    kind: 'area' | 'feature' | 'resource' | 'endpoint';
    /** For an area or a feature, the catalogue constant; for a resource, `module · Resource`; for an endpoint, its path. */
    name: string;
    hasChildren: boolean;
    expanded: boolean;
    actionCount: number;
    viewCount: number;
    automaticCount: number;
    systemCount: number;
    errorCount: number;
    durationSumMs: number;
    /** Undefined where distinct days cannot be derived, which is an area: summing its features would double count days. */
    activeDays?: number;
    lastUsedDay?: string;
    /** Set on feature rows. */
    feature?: UserFeatureUsage;
    /** Set on endpoint rows. */
    endpoint?: FeatureUsageEndpoint;
    /** Set on resource rows, which name git and background entries by their kind rather than a controller. */
    featureKind?: FeatureKind;
    /** Set on area rows: how many of its offered features were used. */
    usedFeatures?: number;
    availableFeatures?: number;
    /** Set on feature rows that have adoption data. */
    adoption?: FeatureAdoption[];
}

/** The endpoints of a feature as the tree groups them: by module and serving controller. */
export interface FeatureResourceGroup {
    key: string;
    module: string;
    /** The controller, or undefined for git and background entries, which are shown by their kind instead. */
    resource?: string;
    featureKind: FeatureKind;
    endpoints: FeatureUsageEndpoint[];
}

/** The windows the admin page offers, in days. The server rejects anything else. */
export const FEATURE_USAGE_WINDOWS_IN_DAYS = [7, 30, 90, 180];

/** The caller roles the report can be filtered to. Mirrors the server-side {@code Role} enum. */
export const FEATURE_USAGE_CALLER_ROLES = ['SUPER_ADMIN', 'ADMIN', 'INSTRUCTOR', 'EDITOR', 'TEACHING_ASSISTANT', 'STUDENT', 'ANONYMOUS'];

/** The product areas in the order the catalogue lists them. Mirrors the server-side {@code ProductArea} enum. */
export const PRODUCT_AREAS = [
    'COURSES',
    'COURSE_MANAGEMENT',
    'EXERCISES',
    'PROGRAMMING',
    'QUIZ',
    'TEXT_MODELING_UPLOAD',
    'ASSESSMENT',
    'EXAMS',
    'LECTURES',
    'COMMUNICATION',
    'NOTIFICATIONS',
    'TUTORIAL_GROUPS',
    'COMPETENCIES',
    'AI_LEARNERS',
    'AI_AUTHORING',
    'INTEGRITY',
    'ACCOUNT',
    'INTEGRATIONS',
    'BUILD_SYSTEM',
    'ADMINISTRATION',
];
