export interface RewriteResult {
    rewrittenText: string;
    result: string | undefined;
    inconsistencies: string[] | undefined;
    suggestions: string[] | undefined;
    improvement: string | undefined;
}
