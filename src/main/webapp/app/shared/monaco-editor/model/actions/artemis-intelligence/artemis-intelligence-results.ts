export interface RewriteResult {
    rewrittenText: string;
    inconsistencies: string[] | undefined;
    suggestions: string[] | undefined;
    improvement: string | undefined;
}

export interface ConsistencyCheckResult {
    inconsistencies: string[] | undefined;
    improvement: string | undefined;
    faqIds: number[] | undefined;
}
