import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';

export type CommentContentType = 'USER' | 'CONSISTENCY_CHECK';

export interface BaseCommentContent {
    contentType: CommentContentType;
}

export interface UserCommentContent extends BaseCommentContent {
    contentType: 'USER';
    text: string;
}

export interface ConsistencyIssueCommentContent extends BaseCommentContent {
    contentType: 'CONSISTENCY_CHECK';
    severity: ConsistencyIssue.SeverityEnum;
    category: ConsistencyIssue.CategoryEnum;
    description: string;
    suggestedFix: string;
    relatedLocations: ArtifactLocation[];
}

export type CommentContent = UserCommentContent | ConsistencyIssueCommentContent;
