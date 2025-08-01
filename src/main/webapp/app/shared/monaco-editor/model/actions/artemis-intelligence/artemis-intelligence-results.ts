export interface RewriteResult {
    rewrittenText: string;
}

export interface ConsistencyCheckResult {
    inconsistencies: string[] | undefined;
    improvement: string | undefined;
    faqIds: number[] | undefined;
}
