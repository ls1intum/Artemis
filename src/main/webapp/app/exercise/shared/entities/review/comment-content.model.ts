import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

export type CommentContentType = 'USER' | 'CONSISTENCY_CHECK';

export interface BaseCommentContent {
    contentType: CommentContentType;
}

export interface UserCommentContent extends BaseCommentContent {
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

export interface ConsistencyIssueCommentContent extends BaseCommentContent {
    contentType: 'CONSISTENCY_CHECK';
    severity: ConsistencyIssue.SeverityEnum;
    category: ConsistencyIssue.CategoryEnum;
    text: string;
    suggestedFix?: InlineCodeChange;
}

export type CommentContent = UserCommentContent | ConsistencyIssueCommentContent;
