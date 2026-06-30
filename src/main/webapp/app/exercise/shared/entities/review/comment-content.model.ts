import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/models/consistency-issue';

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
    // expectedCode/replacementCode may be absent in the response: the server serializes this DTO with
    // @JsonInclude(NON_EMPTY), which omits empty strings (e.g. an empty replacementCode for a pure deletion).
    // Consumers must treat an absent value as an empty string.
    expectedCode?: string;
    replacementCode?: string;
    applied: boolean;
}

export interface ConsistencyIssueCommentContent {
    contentType: CommentContentType.CONSISTENCY_CHECK;
    severity: ConsistencyIssueSeverityEnum;
    category: ConsistencyIssueCategoryEnum;
    text: string;
    suggestedFix?: InlineCodeChange | null;
}

export type CommentContent = UserCommentContent | ConsistencyIssueCommentContent;
