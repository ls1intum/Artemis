import { Injectable, computed, inject, signal } from '@angular/core';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CommentThread, CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CreateComment, UpdateCommentContent } from 'app/exercise/shared/entities/review/comment.model';
import { catchError, of, tap } from 'rxjs';

export enum ReviewCommentOperationType {
    LoadThreads = 'loadThreads',
    CreateThread = 'createThread',
    DeleteComment = 'deleteComment',
    CreateReply = 'createReply',
    UpdateComment = 'updateComment',
    ToggleResolved = 'toggleResolved',
}

export enum ReviewCommentOperationStatus {
    Pending = 'pending',
    Success = 'success',
    Error = 'error',
}

export interface ReviewCommentDraftLocation {
    targetType: CommentThreadLocationType;
    lineNumber: number;
    filePath?: string;
    auxiliaryRepositoryId?: number;
}

export interface ReviewCommentOperation {
    opId: string;
    type: ReviewCommentOperationType;
    target: string;
    status: ReviewCommentOperationStatus;
    error?: string;
}

interface ReviewCommentStoreState {
    exerciseId?: number;
    threads: CommentThread[];
    draftByLocation: Record<string, string>;
    replyDraftByThread: Record<number, string>;
    editDraftByComment: Record<number, string>;
    pendingOps: ReviewCommentOperation[];
}

const MAX_OPERATION_HISTORY = 100;

const initialState: ReviewCommentStoreState = {
    exerciseId: undefined,
    threads: [],
    draftByLocation: {},
    replyDraftByThread: {},
    editDraftByComment: {},
    pendingOps: [],
};

/**
 * Builds the canonical key for comment-draft state at a location.
 *
 * @param location The location of the draft.
 * @returns Stable string key used in store maps and operation targets.
 */
export function buildDraftLocationKey(location: ReviewCommentDraftLocation): string {
    const normalizedPath = location.filePath ?? '';
    const normalizedAuxiliaryRepositoryId = location.auxiliaryRepositoryId ?? '';
    return `${location.targetType}|${normalizedAuxiliaryRepositoryId}|${normalizedPath}|${location.lineNumber}`;
}

@Injectable({ providedIn: 'root' })
export class ReviewCommentStore {
    private readonly reviewCommentService = inject(ExerciseReviewCommentService);
    private readonly alertService = inject(AlertService);

    private readonly state = signal<ReviewCommentStoreState>(initialState);
    private operationSequence = 0;

    readonly threads = computed(() => this.state().threads);
    readonly pendingOps = computed(() => this.state().pendingOps);

    /**
     * Sets the active exercise context and resets review state if it changed.
     *
     * @param exerciseId The active exercise id.
     * @returns True if the context changed and state was reset.
     */
    setExercise(exerciseId: number | undefined): boolean {
        if (this.state().exerciseId === exerciseId) {
            return false;
        }
        this.state.set({
            exerciseId,
            threads: [],
            draftByLocation: {},
            replyDraftByThread: {},
            editDraftByComment: {},
            pendingOps: [],
        });
        return true;
    }

    /**
     * Loads threads for the active exercise and reconciles dependent draft state.
     */
    reloadThreads(): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const target = String(exerciseId);
        const opId = this.beginOperation(ReviewCommentOperationType.LoadThreads, target);
        this.reviewCommentService
            .loadThreads(exerciseId)
            .pipe(
                tap((threads) => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    this.state.update((state) => ({
                        ...state,
                        threads,
                        replyDraftByThread: this.reconcileReplyDrafts(state.replyDraftByThread, threads),
                        editDraftByComment: this.reconcileEditDrafts(state.editDraftByComment, threads),
                    }));
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.loadFailed');
                    if (this.state().exerciseId === exerciseId) {
                        this.state.update((state) => ({ ...state, threads: [] }));
                    }
                    this.alertService.error('artemisApp.review.loadFailed');
                    return of([] as CommentThread[]);
                }),
            )
            .subscribe();
    }

    /**
     * Ensures an empty draft entry exists for a location.
     *
     * @param location The draft location to initialize.
     */
    ensureDraft(location: ReviewCommentDraftLocation): void {
        const key = buildDraftLocationKey(location);
        this.state.update((state) => {
            if (Object.hasOwn(state.draftByLocation, key)) {
                return state;
            }
            return {
                ...state,
                draftByLocation: { ...state.draftByLocation, [key]: '' },
            };
        });
    }

    /**
     * Checks whether a draft exists for a location.
     *
     * @param location The draft location.
     * @returns True if a draft exists.
     */
    hasDraft(location: ReviewCommentDraftLocation): boolean {
        const key = buildDraftLocationKey(location);
        return Object.hasOwn(this.state().draftByLocation, key);
    }

    /**
     * Returns draft text for a location.
     *
     * @param location The draft location.
     * @returns The current draft text.
     */
    getDraftText(location: ReviewCommentDraftLocation): string {
        const key = buildDraftLocationKey(location);
        return this.state().draftByLocation[key] ?? '';
    }

    /**
     * Sets draft text for a location.
     *
     * @param location The draft location.
     * @param text The new draft text.
     */
    setDraftText(location: ReviewCommentDraftLocation, text: string): void {
        const key = buildDraftLocationKey(location);
        this.state.update((state) => ({
            ...state,
            draftByLocation: { ...state.draftByLocation, [key]: text },
        }));
    }

    /**
     * Removes a draft entry for a location.
     *
     * @param location The draft location.
     */
    removeDraft(location: ReviewCommentDraftLocation): void {
        const key = buildDraftLocationKey(location);
        this.state.update((state) => {
            if (!Object.hasOwn(state.draftByLocation, key)) {
                return state;
            }
            const draftByLocation = { ...state.draftByLocation };
            delete draftByLocation[key];
            return { ...state, draftByLocation };
        });
    }

    /**
     * Clears drafts by target type and optional auxiliary repository scope.
     *
     * @param targetType The location target type.
     * @param auxiliaryRepositoryId Optional auxiliary repository filter.
     */
    clearDraftsForTargetType(targetType: CommentThreadLocationType, auxiliaryRepositoryId?: number): void {
        this.state.update((state) => {
            const draftByLocation: Record<string, string> = {};
            for (const [key, value] of Object.entries(state.draftByLocation)) {
                const [entryTargetType, entryAuxiliaryRepositoryId] = key.split('|');
                const matchesTargetType = entryTargetType === targetType;
                const matchesAuxiliaryRepositoryId = auxiliaryRepositoryId === undefined ? true : String(auxiliaryRepositoryId) === (entryAuxiliaryRepositoryId ?? '');
                if (!matchesTargetType || !matchesAuxiliaryRepositoryId) {
                    draftByLocation[key] = value;
                }
            }
            return { ...state, draftByLocation };
        });
    }

    /**
     * Returns reply draft text for a thread.
     *
     * @param threadId The thread id.
     * @returns The current reply draft text.
     */
    getReplyDraft(threadId: number): string {
        return this.state().replyDraftByThread[threadId] ?? '';
    }

    /**
     * Sets reply draft text for a thread.
     *
     * @param threadId The thread id.
     * @param text The new reply draft text.
     */
    setReplyDraft(threadId: number, text: string): void {
        this.state.update((state) => ({
            ...state,
            replyDraftByThread: { ...state.replyDraftByThread, [threadId]: text },
        }));
    }

    /**
     * Clears reply draft text for a thread.
     *
     * @param threadId The thread id.
     */
    clearReplyDraft(threadId: number): void {
        this.state.update((state) => {
            if (!Object.hasOwn(state.replyDraftByThread, threadId)) {
                return state;
            }
            const replyDraftByThread = { ...state.replyDraftByThread };
            delete replyDraftByThread[threadId];
            return { ...state, replyDraftByThread };
        });
    }

    /**
     * Returns edit draft text for a comment.
     *
     * @param commentId The comment id.
     * @returns The current edit draft text.
     */
    getEditDraft(commentId: number): string {
        return this.state().editDraftByComment[commentId] ?? '';
    }

    /**
     * Initializes edit draft text for a comment if absent.
     *
     * @param commentId The comment id.
     * @param initialText Initial text for the edit draft.
     */
    initializeEditDraft(commentId: number, initialText: string): void {
        this.state.update((state) => {
            if (Object.hasOwn(state.editDraftByComment, commentId)) {
                return state;
            }
            return {
                ...state,
                editDraftByComment: { ...state.editDraftByComment, [commentId]: initialText },
            };
        });
    }

    /**
     * Sets edit draft text for a comment.
     *
     * @param commentId The comment id.
     * @param text The new edit draft text.
     */
    setEditDraft(commentId: number, text: string): void {
        this.state.update((state) => ({
            ...state,
            editDraftByComment: { ...state.editDraftByComment, [commentId]: text },
        }));
    }

    /**
     * Clears edit draft text for a comment.
     *
     * @param commentId The comment id.
     */
    clearEditDraft(commentId: number): void {
        this.state.update((state) => {
            if (!Object.hasOwn(state.editDraftByComment, commentId)) {
                return state;
            }
            const editDraftByComment = { ...state.editDraftByComment };
            delete editDraftByComment[commentId];
            return { ...state, editDraftByComment };
        });
    }

    /**
     * Creates a new thread for the active exercise.
     *
     * @param request The thread creation request.
     */
    submitCreateThread(request: CreateCommentThread): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const location: ReviewCommentDraftLocation = {
            targetType: request.targetType,
            lineNumber: request.initialLineNumber,
            filePath: request.initialFilePath,
            auxiliaryRepositoryId: request.auxiliaryRepositoryId,
        };
        const target = buildDraftLocationKey(location);
        const opId = this.beginOperation(ReviewCommentOperationType.CreateThread, target);
        this.reviewCommentService
            .createThread(exerciseId, request)
            .pipe(
                tap((response) => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    const createdThread = response.body;
                    if (createdThread?.id) {
                        const normalizedThread: CommentThread = createdThread.comments ? createdThread : { ...createdThread, comments: [] };
                        this.state.update((state) => ({
                            ...state,
                            threads: this.reviewCommentService.appendThreadToThreads(state.threads, normalizedThread),
                        }));
                        this.removeDraft(location);
                    }
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.saveFailed');
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Deletes a comment in the active exercise and reconciles dependent drafts.
     *
     * @param commentId The comment id to delete.
     */
    deleteComment(commentId: number): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const opId = this.beginOperation(ReviewCommentOperationType.DeleteComment, String(commentId));
        this.reviewCommentService
            .deleteComment(exerciseId, commentId)
            .pipe(
                tap(() => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    this.state.update((state) => {
                        const updatedThreads = this.reviewCommentService.removeCommentFromThreads(state.threads, commentId);
                        return {
                            ...state,
                            threads: updatedThreads,
                            replyDraftByThread: this.reconcileReplyDrafts(state.replyDraftByThread, updatedThreads),
                            editDraftByComment: this.reconcileEditDrafts(state.editDraftByComment, updatedThreads),
                        };
                    });
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.deleteFailed');
                    this.alertService.error('artemisApp.review.deleteFailed');
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Creates a reply comment for a thread in the active exercise.
     *
     * @param threadId The target thread id.
     * @param comment The reply payload.
     */
    createReply(threadId: number, comment: CreateComment): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const opId = this.beginOperation(ReviewCommentOperationType.CreateReply, String(threadId));
        this.reviewCommentService
            .createUserComment(exerciseId, threadId, comment)
            .pipe(
                tap((response) => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    const createdComment = response.body;
                    if (createdComment) {
                        this.state.update((state) => ({
                            ...state,
                            threads: this.reviewCommentService.appendCommentToThreads(state.threads, createdComment),
                        }));
                        this.clearReplyDraft(threadId);
                    }
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.saveFailed');
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Updates an existing comment in the active exercise.
     *
     * @param commentId The comment id to update.
     * @param content The new comment content payload.
     */
    updateComment(commentId: number, content: UpdateCommentContent): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const opId = this.beginOperation(ReviewCommentOperationType.UpdateComment, String(commentId));
        this.reviewCommentService
            .updateUserCommentContent(exerciseId, commentId, content)
            .pipe(
                tap((response) => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    const updatedComment = response.body;
                    if (updatedComment) {
                        this.state.update((state) => ({
                            ...state,
                            threads: this.reviewCommentService.updateCommentInThreads(state.threads, updatedComment),
                        }));
                        this.clearEditDraft(commentId);
                    }
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.saveFailed');
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Updates the resolved state of a thread in the active exercise.
     *
     * @param threadId The thread id.
     * @param resolved The desired resolved state.
     */
    toggleResolved(threadId: number, resolved: boolean): void {
        const exerciseId = this.state().exerciseId;
        if (exerciseId === undefined) {
            return;
        }
        const opId = this.beginOperation(ReviewCommentOperationType.ToggleResolved, String(threadId));
        this.reviewCommentService
            .updateThreadResolvedState(exerciseId, threadId, resolved)
            .pipe(
                tap((response) => {
                    if (this.state().exerciseId !== exerciseId) {
                        return;
                    }
                    const updatedThread = response.body;
                    if (updatedThread?.id) {
                        this.state.update((state) => ({
                            ...state,
                            threads: this.reviewCommentService.replaceThreadInThreads(state.threads, updatedThread),
                        }));
                    }
                    this.finishOperation(opId, ReviewCommentOperationStatus.Success);
                }),
                catchError(() => {
                    this.finishOperation(opId, ReviewCommentOperationStatus.Error, 'artemisApp.review.resolveFailed');
                    this.alertService.error('artemisApp.review.resolveFailed');
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Checks whether an operation of a given type/target is currently pending.
     *
     * @param type The operation type.
     * @param target The operation target identifier.
     * @returns True if a matching pending operation exists.
     */
    isOperationPending(type: ReviewCommentOperationType, target: string): boolean {
        return this.state().pendingOps.some((operation) => operation.type === type && operation.target === target && operation.status === ReviewCommentOperationStatus.Pending);
    }

    /**
     * Appends a pending operation entry and returns its generated id.
     *
     * @param type The operation type.
     * @param target The operation target identifier.
     * @returns The generated operation id.
     */
    private beginOperation(type: ReviewCommentOperationType, target: string): string {
        this.operationSequence += 1;
        const opId = `review-comment-op-${this.operationSequence}`;
        const operation: ReviewCommentOperation = { opId, type, target, status: ReviewCommentOperationStatus.Pending };
        this.state.update((state) => ({ ...state, pendingOps: [...state.pendingOps, operation] }));
        return opId;
    }

    /**
     * Marks an operation as finished and keeps bounded operation history.
     *
     * @param opId The operation id.
     * @param status The completion status.
     * @param error Optional translation key for error messages.
     */
    private finishOperation(opId: string, status: ReviewCommentOperationStatus, error?: string): void {
        this.state.update((state) => {
            const pendingOps = state.pendingOps.map((operation) => {
                if (operation.opId !== opId) {
                    return operation;
                }
                return { ...operation, status, error };
            });
            return { ...state, pendingOps: pendingOps.slice(-MAX_OPERATION_HISTORY) };
        });
    }

    /**
     * Removes reply drafts that no longer belong to existing threads.
     *
     * @param replyDraftByThread Existing reply drafts.
     * @param threads Current thread list.
     * @returns Reply drafts reconciled to valid thread ids.
     */
    private reconcileReplyDrafts(replyDraftByThread: Record<number, string>, threads: CommentThread[]): Record<number, string> {
        const validThreadIds = new Set<number>(threads.map((thread) => thread.id));
        const reconciledDrafts: Record<number, string> = {};
        for (const [threadId, draft] of Object.entries(replyDraftByThread)) {
            const numericThreadId = Number(threadId);
            if (validThreadIds.has(numericThreadId)) {
                reconciledDrafts[numericThreadId] = draft;
            }
        }
        return reconciledDrafts;
    }

    /**
     * Removes edit drafts that no longer belong to existing comments.
     *
     * @param editDraftByComment Existing edit drafts.
     * @param threads Current thread list.
     * @returns Edit drafts reconciled to valid comment ids.
     */
    private reconcileEditDrafts(editDraftByComment: Record<number, string>, threads: CommentThread[]): Record<number, string> {
        const validCommentIds = new Set<number>();
        for (const thread of threads) {
            for (const comment of thread.comments ?? []) {
                validCommentIds.add(comment.id);
            }
        }
        const reconciledDrafts: Record<number, string> = {};
        for (const [commentId, draft] of Object.entries(editDraftByComment)) {
            const numericCommentId = Number(commentId);
            if (validCommentIds.has(numericCommentId)) {
                reconciledDrafts[numericCommentId] = draft;
            }
        }
        return reconciledDrafts;
    }
}
