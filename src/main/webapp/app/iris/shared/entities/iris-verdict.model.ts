export enum IrisVerdict {
    // This must match the API string exactly
    SUSPICIOUS = 'suspicious',
    UNSUSPICIOUS = 'unsuspicious',
}

export enum IrisVerdictReview {
    // This must match the API string exactly
    REVIEWABLE = 'REVIEWABLE',
    NEEDS_REVIEW = 'NEEDS_REVIEW',
    ACCEPTED = 'ACCEPTED',
    REJECTED = 'REJECTED',
}
