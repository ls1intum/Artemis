import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

export enum CommentContentType {
    USER = 'USER',
    CONSISTENCY_CHECK = 'CONSISTENCY_CHECK',
}

export interface UserCommentContent {
    contentType: CommentContentType.USER;
    text: string;
}

export interface InlineCodeChange {
    startLine: number;
    endLine: number;
    expectedCode: string;
    replacementCode: string;
    applied: boolean;
}

export interface ConsistencyIssueCommentContent {
    contentType: CommentContentType.CONSISTENCY_CHECK;
    severity: ConsistencyIssue.SeverityEnum;
    category: ConsistencyIssue.CategoryEnum;
    text: string;
    suggestedFix?: InlineCodeChange;
}

export type CommentContent = UserCommentContent | ConsistencyIssueCommentContent;
