import { Feedback } from 'app/entities/feedback.model';

export class StaticCodeAnalysisIssue {
    public filePath: string;
    public startLine: number;
    public endLine: number;
    public startColumn?: number;
    public endColumn?: number;
    public rule: string;
    public category: string;
    public message: string;
    public priority: string;
    public penalty?: number;

    static fromFeedback(feedback: Feedback): StaticCodeAnalysisIssue {
        return JSON.parse(feedback.detailText!);
    }
}
