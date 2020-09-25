import { Feedback } from 'app/entities/feedback.model';

export class StaticCodeAnalysisIssue {
    public filePath: string;
    public startLine: number;
    public endLine: number;
    public startColumn: number | null;
    public endColumn: number | null;
    public rule: string;
    public category: string;
    public message: string;
    public priority: string;

    static fromFeedback(feedback: Feedback) {
        return JSON.parse(feedback.detailText!) as StaticCodeAnalysisIssue;
    }
}
