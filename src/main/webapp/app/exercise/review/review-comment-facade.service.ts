import { Injectable, computed, inject } from '@angular/core';
import { CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import {
    ReviewCommentDraftLocation,
    ReviewCommentOperationStatus,
    ReviewCommentOperationType,
    ReviewCommentStore,
    buildDraftLocationKey,
} from 'app/exercise/review/review-comment.store';

@Injectable({ providedIn: 'root' })
export class ReviewCommentFacade {
    private readonly store = inject(ReviewCommentStore);

    readonly threads = this.store.threads;
    readonly pendingOps = this.store.pendingOps;
    readonly hasPendingOperations = computed(() => this.pendingOps().some((operation) => operation.status === ReviewCommentOperationStatus.Pending));

    /**
     * Sets the active exercise context for review comments.
     *
     * @param exerciseId The exercise id to activate.
     * @returns True if the exercise context changed, false otherwise.
     */
    setExercise(exerciseId: number | undefined): boolean {
        return this.store.setExercise(exerciseId);
    }

    /**
     * Reloads all threads for the currently active exercise.
     */
    reloadThreads(): void {
        this.store.reloadThreads();
    }

    /**
     * Ensures a draft entry exists for the given location.
     *
     * @param location The draft location.
     */
    ensureDraft(location: ReviewCommentDraftLocation): void {
        this.store.ensureDraft(location);
    }

    /**
     * Checks whether a draft exists at the given location.
     *
     * @param location The draft location.
     * @returns True if a draft exists.
     */
    hasDraft(location: ReviewCommentDraftLocation): boolean {
        return this.store.hasDraft(location);
    }

    /**
     * Returns the draft text for a location.
     *
     * @param location The draft location.
     * @returns The stored draft text or an empty string.
     */
    getDraftText(location: ReviewCommentDraftLocation): string {
        return this.store.getDraftText(location);
    }

    /**
     * Updates draft text for a location.
     *
     * @param location The draft location.
     * @param text The new draft text.
     */
    setDraftText(location: ReviewCommentDraftLocation, text: string): void {
        this.store.setDraftText(location, text);
    }

    /**
     * Removes draft text for a location.
     *
     * @param location The draft location.
     */
    removeDraft(location: ReviewCommentDraftLocation): void {
        this.store.removeDraft(location);
    }

    /**
     * Clears all drafts of a target type, optionally scoped to an auxiliary repository.
     *
     * @param targetType The location target type to clear.
     * @param auxiliaryRepositoryId Optional auxiliary repository id filter.
     */
    clearDraftsForTargetType(targetType: CommentThreadLocationType, auxiliaryRepositoryId?: number): void {
        this.store.clearDraftsForTargetType(targetType, auxiliaryRepositoryId);
    }

    /**
     * Returns the current reply draft text for a thread.
     *
     * @param threadId The thread id.
     * @returns The reply draft text.
     */
    getReplyDraft(threadId: number): string {
        return this.store.getReplyDraft(threadId);
    }

    /**
     * Updates reply draft text for a thread.
     *
     * @param threadId The thread id.
     * @param text The new reply draft text.
     */
    setReplyDraft(threadId: number, text: string): void {
        this.store.setReplyDraft(threadId, text);
    }

    /**
     * Returns the current edit draft text for a comment.
     *
     * @param commentId The comment id.
     * @returns The edit draft text.
     */
    getEditDraft(commentId: number): string {
        return this.store.getEditDraft(commentId);
    }

    /**
     * Initializes edit draft text for a comment if it does not already exist.
     *
     * @param commentId The comment id.
     * @param initialText The initial text to seed.
     */
    startEditDraft(commentId: number, initialText: string): void {
        this.store.initializeEditDraft(commentId, initialText);
    }

    /**
     * Updates edit draft text for a comment.
     *
     * @param commentId The comment id.
     * @param text The new edit draft text.
     */
    setEditDraft(commentId: number, text: string): void {
        this.store.setEditDraft(commentId, text);
    }

    /**
     * Clears edit draft text for a comment.
     *
     * @param commentId The comment id.
     */
    cancelEditDraft(commentId: number): void {
        this.store.clearEditDraft(commentId);
    }

    /**
     * Creates a new thread for the active exercise.
     *
     * @param request Thread creation request.
     */
    submitCreateThread(request: CreateCommentThread): void {
        this.store.submitCreateThread(request);
    }

    /**
     * Deletes a comment by id.
     *
     * @param commentId The comment id to delete.
     */
    deleteComment(commentId: number): void {
        this.store.deleteComment(commentId);
    }

    /**
     * Creates a reply comment in a thread.
     *
     * @param threadId The target thread id.
     * @param comment The reply payload.
     */
    createReply(threadId: number, comment: CreateComment): void {
        this.store.createReply(threadId, comment);
    }

    /**
     * Updates the content of an existing comment.
     *
     * @param commentId The comment id.
     * @param content The updated content payload.
     */
    updateComment(commentId: number, content: UpdateCommentContent): void {
        this.store.updateComment(commentId, content);
    }

    /**
     * Updates the resolved state of a thread.
     *
     * @param threadId The thread id.
     * @param resolved The desired resolved state.
     */
    toggleResolved(threadId: number, resolved: boolean): void {
        this.store.toggleResolved(threadId, resolved);
    }

    /**
     * Checks whether a thread creation operation is pending for a draft location.
     *
     * @param location The draft location used as operation target.
     * @returns True if create-thread is pending for this target.
     */
    isDraftSubmitting(location: ReviewCommentDraftLocation): boolean {
        return this.store.isOperationPending(ReviewCommentOperationType.CreateThread, buildDraftLocationKey(location));
    }

    /**
     * Checks whether a reply creation operation is pending for a thread.
     *
     * @param threadId The thread id.
     * @returns True if a reply create operation is pending.
     */
    isReplySubmitting(threadId: number): boolean {
        return this.store.isOperationPending(ReviewCommentOperationType.CreateReply, String(threadId));
    }

    /**
     * Checks whether a comment update operation is pending.
     *
     * @param commentId The comment id.
     * @returns True if update is pending.
     */
    isEditSubmitting(commentId: number): boolean {
        return this.store.isOperationPending(ReviewCommentOperationType.UpdateComment, String(commentId));
    }

    /**
     * Checks whether a comment delete operation is pending.
     *
     * @param commentId The comment id.
     * @returns True if delete is pending.
     */
    isDeleteSubmitting(commentId: number): boolean {
        return this.store.isOperationPending(ReviewCommentOperationType.DeleteComment, String(commentId));
    }

    /**
     * Checks whether a thread resolve/unresolve operation is pending.
     *
     * @param threadId The thread id.
     * @returns True if resolve toggle is pending.
     */
    isResolveSubmitting(threadId: number): boolean {
        return this.store.isOperationPending(ReviewCommentOperationType.ToggleResolved, String(threadId));
    }
}
