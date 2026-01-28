import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Comment, CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import { CommentThread, CreateCommentThread, UpdateThreadResolvedState } from 'app/exercise/shared/entities/review/comment-thread.model';

type CommentThreadArrayResponseType = HttpResponse<CommentThread[]>;
type CommentThreadResponseType = HttpResponse<CommentThread>;
type CommentResponseType = HttpResponse<Comment>;

@Injectable({ providedIn: 'root' })
export class ExerciseReviewCommentService {
    public resourceUrl = 'api/exercise/exercises';

    private http = inject(HttpClient);

    createThread(exerciseId: number, thread: CreateCommentThread): Observable<CommentThreadResponseType> {
        return this.http.post<CommentThread>(`${this.resourceUrl}/${exerciseId}/review-threads`, thread, { observe: 'response' });
    }

    getThreads(exerciseId: number): Observable<CommentThreadArrayResponseType> {
        return this.http.get<CommentThread[]>(`${this.resourceUrl}/${exerciseId}/review-threads`, { observe: 'response' });
    }

    createUserComment(exerciseId: number, threadId: number, comment: CreateComment): Observable<CommentResponseType> {
        return this.http.post<Comment>(`${this.resourceUrl}/${exerciseId}/review-threads/${threadId}/comments`, comment, { observe: 'response' });
    }

    updateThreadResolvedState(exerciseId: number, threadId: number, resolved: boolean): Observable<CommentThreadResponseType> {
        const body: UpdateThreadResolvedState = { resolved };
        return this.http.put<CommentThread>(`${this.resourceUrl}/${exerciseId}/review-threads/${threadId}/resolved`, body, { observe: 'response' });
    }

    updateUserCommentContent(exerciseId: number, commentId: number, content: UpdateCommentContent): Observable<CommentResponseType> {
        return this.http.put<Comment>(`${this.resourceUrl}/${exerciseId}/review-comments/${commentId}`, content, { observe: 'response' });
    }

    deleteComment(exerciseId: number, commentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/review-comments/${commentId}`, { observe: 'response' });
    }

    removeCommentFromThreads(threads: CommentThread[], commentId: number): CommentThread[] {
        return threads
            .map((thread) => {
                if (!thread.comments) {
                    return thread;
                }
                const remainingComments = thread.comments.filter((comment) => comment.id !== commentId);
                if (remainingComments.length === thread.comments.length) {
                    return thread;
                }
                return { ...thread, comments: remainingComments };
            })
            .filter((thread) => !thread.comments || thread.comments.length > 0);
    }

    appendCommentToThreads(threads: CommentThread[], createdComment: Comment): CommentThread[] {
        if (!createdComment.threadId) {
            return threads;
        }
        return threads.map((thread) => {
            if (thread.id !== createdComment.threadId) {
                return thread;
            }
            const comments = thread.comments ?? [];
            return { ...thread, comments: [...comments, createdComment] };
        });
    }

    updateCommentInThreads(threads: CommentThread[], updatedComment: Comment): CommentThread[] {
        if (!updatedComment.id || !updatedComment.threadId) {
            return threads;
        }
        return threads.map((thread) => {
            if (thread.id !== updatedComment.threadId || !thread.comments) {
                return thread;
            }
            return {
                ...thread,
                comments: thread.comments.map((comment) => (comment.id === updatedComment.id ? { ...comment, ...updatedComment } : comment)),
            };
        });
    }

    replaceThreadInThreads(threads: CommentThread[], updatedThread: CommentThread): CommentThread[] {
        if (!updatedThread.id) {
            return threads;
        }
        return threads.map((thread) => (thread.id === updatedThread.id ? updatedThread : thread));
    }

    appendThreadToThreads(threads: CommentThread[], newThread: CommentThread): CommentThread[] {
        if (!newThread.id) {
            return threads;
        }
        return [...threads, newThread];
    }
}
