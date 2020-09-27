export class StaticCodeAnalysisIssue {
    public filePath: string;
    public startLine: number;
    public endLine: number;
    public startColumn?: number;
    public endColumn: number | null;
    public rule: string;
    public category: string;
    public message: string;
    public priority: string;
}
