import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

export type CommentContentType = 'USER' | 'CONSISTENCY_CHECK';

export interface UserCommentContent {
    contentType: 'USER';
    text: string;
}

export interface InlineCodeChange {
    startLine?: number;
    endLine?: number;
    expectedCode?: string;
    replacementCode?: string;
    applied?: boolean;
}

export interface ConsistencyIssueCommentContent {
    contentType: 'CONSISTENCY_CHECK';
    severity: ConsistencyIssue.SeverityEnum;
    category: ConsistencyIssue.CategoryEnum;
    text: string;
    suggestedFix?: InlineCodeChange;
}

export type CommentContent = UserCommentContent | ConsistencyIssueCommentContent;
