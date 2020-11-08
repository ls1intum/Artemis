interface JPlagComparison {
    matches: JPlagMatch[];
    subA: JPlagSubmission;
    subB: JPlagSubmission;
    numberOfMatchedTokens: number;
    bcMatchesA: any;
    bcMatchesB: any;
}
