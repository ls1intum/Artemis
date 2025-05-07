interface RewriteResult {
    result: string;
    inconsistencies: string[];
    suggestions: string[];
    improvement: string;
}

export default RewriteResult;
