/**
 * The verdict of an Iris assessment on whether a submission is suspicious.
 */
export enum IrisVerdict {
    // This must match the API string exactly
    SUSPICIOUS = 'SUSPICIOUS',
    UNSUSPICIOUS = 'UNSUSPICIOUS',
}

/**
 * An instructor's review decision on an Iris assessment verdict.
 */
export enum IrisVerdictReview {
    // This must match the API string exactly
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
}
