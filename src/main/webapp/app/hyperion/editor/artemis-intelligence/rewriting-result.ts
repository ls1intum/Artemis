export interface RewriteResult {
    result: string | undefined;
    inconsistencies: string[] | undefined;
    suggestions: string[] | undefined;
    improvement: string | undefined;
}
