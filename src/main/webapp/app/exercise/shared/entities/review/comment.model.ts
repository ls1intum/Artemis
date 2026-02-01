import { CommentContent, UserCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';

export enum CommentType {
    USER = 'USER',
    CONSISTENCY_CHECK = 'CONSISTENCY_CHECK',
}

export interface Comment {
    id: number;
    threadId: number;
    authorName?: string;
    type: CommentType;
    content: CommentContent;
    initialVersionId?: number;
    initialCommitSha?: string;
    createdDate?: string;
    lastModifiedDate?: string;
}

export type CreateComment = UserCommentContent;

export type UpdateCommentContent = UserCommentContent;
