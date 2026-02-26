import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { Comment, CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import { CommentThread, CreateCommentThread, UpdateThreadResolvedState } from 'app/exercise/shared/entities/review/comment-thread.model';
import { AlertService } from 'app/shared/service/alert.service';

type CommentThreadArrayResponseType = HttpResponse<CommentThread[]>;
type CommentThreadResponseType = HttpResponse<CommentThread>;
type CommentResponseType = HttpResponse<Comment>;
type ReviewCommentSuccessCallback = () => void;

@Injectable({ providedIn: 'root' })
export class ExerciseReviewCommentService {
    public readonly resourceUrl = 'api/exercise/exercises';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private activeExerciseId?: number;

    /**
     * Source of truth for currently loaded review comment threads.
     * This intentionally tracks only persisted comments/threads, not draft input state.
     */
    readonly threads = signal<CommentThread[]>([]);

    /**
     * Sets the active exercise context and clears thread state when it changes.
     *
     * @param exerciseId The currently active exercise id.
     * @returns True if the context changed.
     */
    setExercise(exerciseId: number | undefined): boolean {
        if (this.activeExerciseId === exerciseId) {
            return false;
        }
        this.activeExerciseId = exerciseId;
        this.threads.set([]);
        return true;
    }

    /**
     * Reloads threads for the active exercise context.
     */
    reloadThreads(): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            this.threads.set([]);
            return;
        }
        this.loadThreads(exerciseId).subscribe({
            next: (threads) => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.threads.set(threads);
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.threads.set([]);
                this.alertService.error('artemisApp.review.loadFailed');
            },
        });
    }

    /**
     * Creates a thread in the active exercise and reconciles local thread state.
     *
     * @param thread The thread payload.
     * @param onSuccess Callback invoked only after successful backend persistence.
     */
    createThreadInContext(thread: CreateCommentThread, onSuccess?: ReviewCommentSuccessCallback): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            return;
        }
        this.createThread(exerciseId, thread).subscribe({
            next: (response) => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                const createdThread = response.body;
                if (!createdThread?.id) {
                    return;
                }
                const normalizedThread: CommentThread = createdThread.comments ? createdThread : Object.assign({}, createdThread, { comments: [] });
                this.threads.update((threads) => this.appendThreadToThreads(threads, normalizedThread));
                onSuccess?.();
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.alertService.error('artemisApp.review.saveFailed');
            },
        });
    }

    /**
     * Deletes a comment in the active exercise and reconciles local thread state.
     *
     * @param commentId The comment id to delete.
     */
    deleteCommentInContext(commentId: number): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            return;
        }
        this.deleteComment(exerciseId, commentId).subscribe({
            next: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.threads.update((threads) => this.removeCommentFromThreads(threads, commentId));
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.alertService.error('artemisApp.review.deleteFailed');
            },
        });
    }

    /**
     * Creates a reply in the active exercise and reconciles local thread state.
     *
     * @param threadId The target thread id.
     * @param comment The reply payload.
     * @param onSuccess Callback invoked only after successful backend persistence.
     */
    createReplyInContext(threadId: number, comment: CreateComment, onSuccess?: ReviewCommentSuccessCallback): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            return;
        }
        this.createUserComment(exerciseId, threadId, comment).subscribe({
            next: (response) => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                const createdComment = response.body;
                if (!createdComment) {
                    return;
                }
                this.threads.update((threads) => this.appendCommentToThreads(threads, createdComment));
                onSuccess?.();
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.alertService.error('artemisApp.review.saveFailed');
            },
        });
    }

    /**
     * Updates a comment in the active exercise and reconciles local thread state.
     *
     * @param commentId The comment id to update.
     * @param content The updated content payload.
     * @param onSuccess Callback invoked only after successful backend persistence.
     */
    updateCommentInContext(commentId: number, content: UpdateCommentContent, onSuccess?: ReviewCommentSuccessCallback): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            return;
        }
        this.updateUserCommentContent(exerciseId, commentId, content).subscribe({
            next: (response) => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                const updatedComment = response.body;
                if (!updatedComment) {
                    return;
                }
                this.threads.update((threads) => this.updateCommentInThreads(threads, updatedComment));
                onSuccess?.();
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.alertService.error('artemisApp.review.saveFailed');
            },
        });
    }

    /**
     * Toggles thread resolved state in the active exercise and reconciles local thread state.
     *
     * @param threadId The thread id.
     * @param resolved The desired resolved state.
     */
    toggleResolvedInContext(threadId: number, resolved: boolean): void {
        const exerciseId = this.activeExerciseId;
        if (!exerciseId) {
            return;
        }
        this.updateThreadResolvedState(exerciseId, threadId, resolved).subscribe({
            next: (response) => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                const updatedThread = response.body;
                if (!updatedThread?.id) {
                    return;
                }
                this.threads.update((threads) => this.replaceThreadInThreads(threads, updatedThread));
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId) {
                    return;
                }
                this.alertService.error('artemisApp.review.resolveFailed');
            },
        });
    }

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
     * Loads all review comment threads for an exercise and unwraps the response body.
     *
     * @param exerciseId The exercise to fetch threads for.
     * @returns The thread list from the response body.
     */
    loadThreads(exerciseId: number): Observable<CommentThread[]> {
        return this.getThreads(exerciseId).pipe(map((response) => response.body ?? []));
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
                return Object.assign({}, thread, { comments: remainingComments });
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
        if (createdComment.threadId === undefined) {
            return threads;
        }
        return threads.map((thread) => {
            if (thread.id !== createdComment.threadId) {
                return thread;
            }
            const comments = thread.comments ?? [];
            return Object.assign({}, thread, { comments: [...comments, createdComment] });
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
        if (updatedComment.id === undefined || updatedComment.threadId === undefined) {
            return threads;
        }
        return threads.map((thread) => {
            if (thread.id !== updatedComment.threadId || !thread.comments) {
                return thread;
            }
            return Object.assign({}, thread, {
                comments: thread.comments.map((comment) => (comment.id === updatedComment.id ? Object.assign({}, comment, updatedComment) : comment)),
            });
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
