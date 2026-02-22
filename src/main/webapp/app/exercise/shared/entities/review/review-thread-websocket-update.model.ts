import { Comment } from 'app/exercise/shared/entities/review/comment.model';
import { CommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';

export enum ReviewThreadWebsocketAction {
    THREAD_CREATED = 'THREAD_CREATED',
    THREAD_UPDATED = 'THREAD_UPDATED',
    COMMENT_CREATED = 'COMMENT_CREATED',
    COMMENT_UPDATED = 'COMMENT_UPDATED',
    COMMENT_DELETED = 'COMMENT_DELETED',
    GROUP_UPDATED = 'GROUP_UPDATED',
}

export interface ReviewThreadWebsocketUpdate {
    action: ReviewThreadWebsocketAction;
    exerciseId: number;
    thread?: CommentThread;
    comment?: Comment;
    commentId?: number;
    threadIds?: number[];
    groupId?: number;
}
