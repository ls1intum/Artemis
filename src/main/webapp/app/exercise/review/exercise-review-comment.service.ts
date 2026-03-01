import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, Subscription, map } from 'rxjs';
import { Comment, CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import { CommentThread, CreateCommentThread, UpdateThreadResolvedState } from 'app/exercise/shared/entities/review/comment-thread.model';
import { AlertService } from 'app/shared/service/alert.service';
import { ReviewThreadSyncAction, ReviewThreadSyncUpdate } from 'app/exercise/shared/entities/review/review-thread-sync-update.model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ReviewThreadSyncUpdateEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';

type CommentThreadArrayResponseType = HttpResponse<CommentThread[]>;
type CommentThreadResponseType = HttpResponse<CommentThread>;
type CommentResponseType = HttpResponse<Comment>;
type ReviewCommentSuccessCallback = () => void;

@Injectable({ providedIn: 'root' })
export class ExerciseReviewCommentService implements OnDestroy {
    public readonly resourceUrl = 'api/exercise/exercises';

    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private exerciseEditorSyncService = inject(ExerciseEditorSyncService);
    private activeExerciseId?: number;
    private synchronizationSubscription?: Subscription;
    private subscribedExerciseId?: number;
    private isReloading = false;
    private pendingSyncUpdates: ReviewThreadSyncUpdate[] = [];
    private reloadSequence = 0;

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
        this.reloadSequence++;
        this.isReloading = false;
        this.pendingSyncUpdates = [];
        this.activeExerciseId = exerciseId;
        this.threads.set([]);
        this.synchronizationSubscription?.unsubscribe();
        this.synchronizationSubscription = undefined;
        this.subscribedExerciseId = undefined;
        if (exerciseId) {
            this.ensureSynchronizationSubscription(exerciseId);
        }
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
        const reloadId = ++this.reloadSequence;
        this.isReloading = true;
        this.pendingSyncUpdates = [];
        this.loadThreads(exerciseId).subscribe({
            next: (threads) => {
                if (this.activeExerciseId !== exerciseId || this.reloadSequence !== reloadId) {
                    return;
                }
                this.threads.set(this.applyQueuedSyncUpdates(threads, this.pendingSyncUpdates));
                this.pendingSyncUpdates = [];
                this.isReloading = false;
                this.ensureSynchronizationSubscription(exerciseId);
            },
            error: () => {
                if (this.activeExerciseId !== exerciseId || this.reloadSequence !== reloadId) {
                    return;
                }
                this.threads.set(this.applyQueuedSyncUpdates([], this.pendingSyncUpdates));
                this.pendingSyncUpdates = [];
                this.isReloading = false;
                this.alertService.error('artemisApp.review.loadFailed');
                this.ensureSynchronizationSubscription(exerciseId);
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
            if (createdComment.id !== undefined && comments.some((comment) => comment.id === createdComment.id)) {
                return thread;
            }
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
        if (threads.some((thread) => thread.id === newThread.id)) {
            return threads;
        }
        return [...threads, newThread];
    }

    /**
     * Merges a thread update into the current list while preserving any comments that might have been
     * received through dedicated comment events but are not present on the incoming thread payload.
     *
     * @param threads The current thread list.
     * @param updatedThread The updated thread from synchronization events.
     * @returns The merged thread list.
     */
    private mergeThreadUpdateInThreads(threads: CommentThread[], updatedThread: CommentThread): CommentThread[] {
        if (!updatedThread.id) {
            return threads;
        }
        const existingThread = threads.find((thread) => thread.id === updatedThread.id);
        if (!existingThread) {
            return [...threads, updatedThread];
        }
        const existingComments = existingThread.comments ?? [];
        const incomingComments = updatedThread.comments ?? [];
        const mergedComments = this.mergeThreadComments(existingComments, incomingComments);
        return threads.map((thread) => {
            if (thread.id !== updatedThread.id) {
                return thread;
            }
            return Object.assign({}, thread, updatedThread, { comments: mergedComments });
        });
    }

    /**
     * Merges comments by id and prefers the most recently modified representation for duplicates.
     *
     * @param existingComments Comments already present locally.
     * @param incomingComments Comments provided by the incoming thread update.
     * @returns A merged comment list.
     */
    private mergeThreadComments(existingComments: Comment[], incomingComments: Comment[]): Comment[] {
        const commentsById = new Map<number, Comment>();
        for (const comment of existingComments) {
            if (comment?.id !== undefined) {
                commentsById.set(comment.id, comment);
            }
        }
        for (const comment of incomingComments) {
            if (comment?.id === undefined) {
                continue;
            }
            const existing = commentsById.get(comment.id);
            commentsById.set(comment.id, existing ? this.pickMostRecentComment(existing, comment) : comment);
        }
        return Array.from(commentsById.values());
    }

    /**
     * Chooses the newer comment representation by comparing modification timestamps.
     *
     * @param existingComment The currently stored comment.
     * @param incomingComment The incoming comment payload.
     * @returns The comment considered most recent.
     */
    private pickMostRecentComment(existingComment: Comment, incomingComment: Comment): Comment {
        const existingTimestamp = this.toTimestamp(existingComment.lastModifiedDate ?? existingComment.createdDate);
        const incomingTimestamp = this.toTimestamp(incomingComment.lastModifiedDate ?? incomingComment.createdDate);
        if (existingTimestamp !== undefined && incomingTimestamp !== undefined) {
            return incomingTimestamp >= existingTimestamp ? incomingComment : existingComment;
        }
        if (incomingTimestamp !== undefined) {
            return incomingComment;
        }
        if (existingTimestamp !== undefined) {
            return existingComment;
        }
        return incomingComment;
    }

    /**
     * Parses an ISO-like timestamp string into milliseconds since epoch.
     *
     * @param timestamp The timestamp string to parse.
     * @returns Milliseconds since epoch, or undefined if parsing fails.
     */
    private toTimestamp(timestamp?: string): number | undefined {
        if (!timestamp) {
            return undefined;
        }
        const parsed = Date.parse(timestamp);
        return Number.isNaN(parsed) ? undefined : parsed;
    }

    private updateGroupInThreads(threads: CommentThread[], threadIds: number[], groupId?: number): CommentThread[] {
        if (!threadIds || threadIds.length === 0) {
            return threads;
        }
        const affectedThreadIds = new Set(threadIds);
        return threads.map((thread) => {
            if (!affectedThreadIds.has(thread.id)) {
                return thread;
            }
            return Object.assign({}, thread, { groupId });
        });
    }

    /**
     * Applies a single synchronization update for the active exercise.
     *
     * During a reload, updates are queued and replayed after the REST snapshot arrives to avoid
     * race conditions between snapshot and incremental events.
     *
     * @param update The incoming synchronization update.
     */
    private applySyncUpdate(update: ReviewThreadSyncUpdate): void {
        if (!update || !this.activeExerciseId || update.exerciseId !== this.activeExerciseId) {
            return;
        }
        if (this.isReloading) {
            this.pendingSyncUpdates.push(update);
            return;
        }
        this.threads.update((threads) => {
            return this.applySyncUpdateToThreads(threads, update);
        });
    }

    /**
     * Replays queued synchronization updates on top of a freshly loaded thread snapshot.
     *
     * @param threads The thread snapshot from REST.
     * @param updates The queued synchronization updates in arrival order.
     * @returns The merged thread state.
     */
    private applyQueuedSyncUpdates(threads: CommentThread[], updates: ReviewThreadSyncUpdate[]): CommentThread[] {
        return updates.reduce((accumulator, update) => this.applySyncUpdateToThreads(accumulator, update), threads);
    }

    /**
     * Filters synchronization events and forwards only review thread updates.
     *
     * @param event The synchronization event received from the shared topic.
     */
    private handleSynchronizationEvent(event: ExerciseEditorSyncEvent): void {
        if (event.eventType !== ExerciseEditorSyncEventType.REVIEW_THREAD_UPDATE || event.target !== ExerciseEditorSyncTarget.REVIEW_COMMENTS) {
            return;
        }
        const reviewUpdateEvent = event as ReviewThreadSyncUpdateEvent;
        const reviewUpdate: ReviewThreadSyncUpdate = {
            action: reviewUpdateEvent.action,
            exerciseId: reviewUpdateEvent.exerciseId,
            thread: reviewUpdateEvent.thread,
            comment: reviewUpdateEvent.comment,
            commentId: reviewUpdateEvent.commentId,
            threadIds: reviewUpdateEvent.threadIds,
            groupId: reviewUpdateEvent.groupId,
        };
        this.applySyncUpdate(reviewUpdate);
    }

    /**
     * Applies one synchronization event to the current thread collection using idempotent reducers.
     *
     * Note: The initiating client can process the same logical change via HTTP and the echoed synchronization event.
     * This is acceptable because reducers are idempotent, and the widget manager rerenders only when state changes.
     *
     * @param threads The current thread list.
     * @param update The synchronization update to apply.
     * @returns The updated thread list.
     */
    private applySyncUpdateToThreads(threads: CommentThread[], update: ReviewThreadSyncUpdate): CommentThread[] {
        switch (update.action) {
            case ReviewThreadSyncAction.THREAD_CREATED:
                if (!update.thread) {
                    return threads;
                }
                return this.appendThreadToThreads(threads, update.thread);
            case ReviewThreadSyncAction.THREAD_UPDATED:
                if (!update.thread) {
                    return threads;
                }
                return this.mergeThreadUpdateInThreads(threads, update.thread);
            case ReviewThreadSyncAction.COMMENT_CREATED:
                if (!update.comment) {
                    return threads;
                }
                return this.appendCommentToThreads(threads, update.comment);
            case ReviewThreadSyncAction.COMMENT_UPDATED:
                if (!update.comment) {
                    return threads;
                }
                return this.updateCommentInThreads(threads, update.comment);
            case ReviewThreadSyncAction.COMMENT_DELETED:
                if (!update.commentId) {
                    return threads;
                }
                return this.removeCommentFromThreads(threads, update.commentId);
            case ReviewThreadSyncAction.GROUP_UPDATED:
                if (!update.threadIds) {
                    return threads;
                }
                return this.updateGroupInThreads(threads, update.threadIds, update.groupId);
            default:
                return threads;
        }
    }

    /**
     * Ensures an active synchronization subscription for review updates and replaces stale subscriptions.
     *
     * @param exerciseId The exercise id whose review updates should be observed.
     */
    private ensureSynchronizationSubscription(exerciseId: number): void {
        if (this.subscribedExerciseId === exerciseId && this.synchronizationSubscription) {
            return;
        }
        this.synchronizationSubscription?.unsubscribe();
        try {
            this.synchronizationSubscription = this.exerciseEditorSyncService.subscribeToUpdates().subscribe((event) => this.handleSynchronizationEvent(event));
            this.subscribedExerciseId = exerciseId;
        } catch {
            // Parent containers establish the shared synchronization connection. If it is not
            // connected yet, later reload/context transitions retry subscription setup.
            this.synchronizationSubscription = undefined;
            this.subscribedExerciseId = undefined;
        }
    }

    ngOnDestroy(): void {
        this.synchronizationSubscription?.unsubscribe();
        this.synchronizationSubscription = undefined;
        this.subscribedExerciseId = undefined;
        this.pendingSyncUpdates = [];
        this.isReloading = false;
    }
}
