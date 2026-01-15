import { CommentContent } from 'app/exercise/shared/entities/review/comment-content.model';

export enum CommentType {
    USER = 'USER',
    CONSISTENCY_CHECK = 'CONSISTENCY_CHECK',
}

export interface Comment {
    id: number;
    threadId: number;
    authorId?: number;
    authorName?: string;
    type: CommentType;
    content: CommentContent;
    createdDate?: string;
    lastModifiedDate?: string;
}

export interface CreateComment {
    type: CommentType;
    content: CommentContent;
}

export interface UpdateCommentContent {
    content: CommentContent;
}
