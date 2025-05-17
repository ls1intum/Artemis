interface RewriteResult {
    result: string | undefined;
    inconsistencies: string[];
    suggestions: string[];
    improvement: string;
}

export default RewriteResult;
