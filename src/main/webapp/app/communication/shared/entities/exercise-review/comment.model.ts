import { CommentContent } from 'app/communication/shared/entities/exercise-review/comment-content.model';

export enum CommentType {
    USER = 'USER',
    CONSISTENCY_CHECK = 'CONSISTENCY_CHECK',
}

export interface Comment {
    id: number;
    threadId: number;
    authorId?: number;
    inReplyToId?: number;
    type: CommentType;
    content: CommentContent;
    createdDate?: string;
    lastModifiedDate?: string;
}

export interface CreateComment {
    inReplyToId?: number;
    type: CommentType;
    content: CommentContent;
}

export interface UpdateCommentContent {
    content: CommentContent;
}
