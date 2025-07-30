export interface RewriteResult {
    rewrittenText: string;
}

export interface ConsistencyCheckResult {
    inconsistencies: string[] | undefined;
    suggestions: string[] | undefined;
    improvement: string | undefined;
}
