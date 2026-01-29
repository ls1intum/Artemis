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

    /**
     * Creates a new review comment thread.
     *
     * @param exerciseId The exercise that owns the thread.
     * @param thread The thread payload to create.
     * @returns The HTTP response observable containing the created thread.
     */
    createThread(exerciseId: number, thread: CreateCommentThread): Observable<CommentThreadResponseType> {
        return this.http.post<CommentThread>(`${this.resourceUrl}/${exerciseId}/review-threads`, thread, { observe: 'response' });
    }

    /**
     * Loads all review comment threads for an exercise.
     *
     * @param exerciseId The exercise to fetch threads for.
     * @returns The HTTP response observable containing the threads.
     */
    getThreads(exerciseId: number): Observable<CommentThreadArrayResponseType> {
        return this.http.get<CommentThread[]>(`${this.resourceUrl}/${exerciseId}/review-threads`, { observe: 'response' });
    }

    /**
     * Creates a user comment within a thread.
     *
     * @param exerciseId The exercise that owns the thread.
     * @param threadId The thread to add the comment to.
     * @param comment The comment payload to create.
     * @returns The HTTP response observable containing the created comment.
     */
    createUserComment(exerciseId: number, threadId: number, comment: CreateComment): Observable<CommentResponseType> {
        return this.http.post<Comment>(`${this.resourceUrl}/${exerciseId}/review-threads/${threadId}/comments`, comment, { observe: 'response' });
    }

    /**
     * Updates the resolved state of a thread.
     *
     * @param exerciseId The exercise that owns the thread.
     * @param threadId The thread to update.
     * @param resolved The new resolved state.
     * @returns The HTTP response observable containing the updated thread.
     */
    updateThreadResolvedState(exerciseId: number, threadId: number, resolved: boolean): Observable<CommentThreadResponseType> {
        const body: UpdateThreadResolvedState = { resolved };
        return this.http.put<CommentThread>(`${this.resourceUrl}/${exerciseId}/review-threads/${threadId}/resolved`, body, { observe: 'response' });
    }

    /**
     * Updates the content of a user comment.
     *
     * @param exerciseId The exercise that owns the comment.
     * @param commentId The comment to update.
     * @param content The new comment content.
     * @returns The HTTP response observable containing the updated comment.
     */
    updateUserCommentContent(exerciseId: number, commentId: number, content: UpdateCommentContent): Observable<CommentResponseType> {
        return this.http.put<Comment>(`${this.resourceUrl}/${exerciseId}/review-comments/${commentId}`, content, { observe: 'response' });
    }

    /**
     * Deletes a review comment by id.
     *
     * @param exerciseId The exercise that owns the comment.
     * @param commentId The id of the comment to delete.
     * @returns The HTTP response observable.
     */
    deleteComment(exerciseId: number, commentId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${exerciseId}/review-comments/${commentId}`, { observe: 'response' });
    }

    /**
     * Removes a comment from all threads and drops threads that become empty.
     *
     * @param threads The current list of threads.
     * @param commentId The id of the comment to remove.
     * @returns The updated thread list.
     */
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

    /**
     * Appends a newly created comment to the matching thread.
     *
     * @param threads The current list of threads.
     * @param createdComment The new comment returned by the server.
     * @returns The updated thread list.
     */
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

    /**
     * Replaces a comment in its thread with the updated server version.
     *
     * @param threads The current list of threads.
     * @param updatedComment The updated comment returned by the server.
     * @returns The updated thread list.
     */
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

    /**
     * Replaces a single thread in the list with the updated server version.
     *
     * @param threads The current list of threads.
     * @param updatedThread The updated thread returned by the server.
     * @returns The updated thread list.
     */
    replaceThreadInThreads(threads: CommentThread[], updatedThread: CommentThread): CommentThread[] {
        if (!updatedThread.id) {
            return threads;
        }
        return threads.map((thread) => (thread.id === updatedThread.id ? updatedThread : thread));
    }

    /**
     * Appends a newly created thread to the list.
     *
     * @param threads The current list of threads.
     * @param newThread The newly created thread.
     * @returns The updated thread list.
     */
    appendThreadToThreads(threads: CommentThread[], newThread: CommentThread): CommentThread[] {
        if (!newThread.id) {
            return threads;
        }
        return [...threads, newThread];
    }
}
