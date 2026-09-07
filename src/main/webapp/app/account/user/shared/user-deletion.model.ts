export type UserDeletionAction = 'DELETE' | 'REMOVE_MEMBERSHIP' | 'DETACH_ACTOR';

export type UserDeletionDataCategory =
    | 'ACCOUNT'
    | 'ASSESSMENT'
    | 'COMMUNICATION'
    | 'COURSE_MEMBERSHIP'
    | 'COURSE_REQUEST'
    | 'EXAM'
    | 'LEARNING_ANALYTICS'
    | 'LTI'
    | 'PARTICIPATION'
    | 'PLAGIARISM'
    | 'TEAM'
    | 'TUTORIAL_GROUP';

export interface UserDeletionImpactCategory {
    category: UserDeletionDataCategory;
    action: UserDeletionAction;
    count: number;
}

export interface UserDeletionImpact {
    userId: number;
    login: string;
    automaticEligible: boolean;
    legacyDeleted: boolean;
    retentionOverrideRequired: boolean;
    totalAffectedObjects: number;
    impactFingerprint: string;
    categories: UserDeletionImpactCategory[];
}

export interface BulkUserDeletionImpact {
    users: UserDeletionImpact[];
    totalAffectedObjects: number;
    categories: UserDeletionImpactCategory[];
}

export interface UserDeletionConfirmation {
    login: string;
    impactFingerprint: string;
}

export interface BulkUserDeletionRequest {
    users: UserDeletionConfirmation[];
}

export type UserDeletionResultStatus = 'DELETED' | 'PLAN_CHANGED' | 'BLOCKED' | 'FORBIDDEN' | 'FAILED';

export interface UserDeletionResult {
    userId?: number;
    login: string;
    status: UserDeletionResultStatus;
    reason?: string;
}
