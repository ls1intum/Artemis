import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WritableSignal, signal } from '@angular/core';
import { ReviewCommentFacade } from 'app/exercise/review/review-comment-facade.service';
import { ReviewCommentOperationStatus, ReviewCommentOperationType, ReviewCommentStore, buildDraftLocationKey } from 'app/exercise/review/review-comment.store';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';

describe('ReviewCommentFacade', () => {
    setupTestBed({ zoneless: true });

    let facade: ReviewCommentFacade;
    let storeMock: any;
    let pendingOpsSignal: WritableSignal<any[]>;
    let threadsSignal: WritableSignal<any[]>;

    beforeEach(() => {
        pendingOpsSignal = signal([]);
        threadsSignal = signal([]);
        storeMock = {
            threads: threadsSignal.asReadonly(),
            pendingOps: pendingOpsSignal.asReadonly(),
            setExercise: vi.fn(() => true),
            reloadThreads: vi.fn(),
            ensureDraft: vi.fn(),
            hasDraft: vi.fn(() => false),
            getDraftText: vi.fn(() => ''),
            setDraftText: vi.fn(),
            removeDraft: vi.fn(),
            clearDraftsForTargetType: vi.fn(),
            getReplyDraft: vi.fn(() => ''),
            setReplyDraft: vi.fn(),
            getEditDraft: vi.fn(() => ''),
            initializeEditDraft: vi.fn(),
            setEditDraft: vi.fn(),
            clearEditDraft: vi.fn(),
            submitCreateThread: vi.fn(),
            deleteComment: vi.fn(),
            createReply: vi.fn(),
            updateComment: vi.fn(),
            toggleResolved: vi.fn(),
            isOperationPending: vi.fn(() => false),
        };

        TestBed.configureTestingModule({
            providers: [ReviewCommentFacade, { provide: ReviewCommentStore, useValue: storeMock }],
        });

        facade = TestBed.inject(ReviewCommentFacade);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should delegate setExercise and return the store result', () => {
        storeMock.setExercise.mockReturnValue(false);

        const changed = facade.setExercise(42);

        expect(changed).toBe(false);
        expect(storeMock.setExercise).toHaveBeenCalledWith(42);
    });

    it('should compute hasPendingOperations from pending operation status', () => {
        expect(facade.hasPendingOperations()).toBe(false);

        pendingOpsSignal.set([
            {
                opId: 'review-comment-op-1',
                type: ReviewCommentOperationType.LoadThreads,
                target: '1',
                status: ReviewCommentOperationStatus.Pending,
            },
        ]);
        expect(facade.hasPendingOperations()).toBe(true);

        pendingOpsSignal.set([
            {
                opId: 'review-comment-op-1',
                type: ReviewCommentOperationType.LoadThreads,
                target: '1',
                status: ReviewCommentOperationStatus.Success,
            },
        ]);
        expect(facade.hasPendingOperations()).toBe(false);
    });

    it('should query draft submission state by location key', () => {
        const location = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            lineNumber: 5,
            filePath: 'src/File.java',
        };
        storeMock.isOperationPending.mockReturnValue(true);

        const submitting = facade.isDraftSubmitting(location);

        expect(submitting).toBe(true);
        expect(storeMock.isOperationPending).toHaveBeenCalledWith(ReviewCommentOperationType.CreateThread, buildDraftLocationKey(location));
    });

    it('should query reply/edit/delete/resolve submission states with correct targets', () => {
        facade.isReplySubmitting(11);
        facade.isEditSubmitting(12);
        facade.isDeleteSubmitting(13);
        facade.isResolveSubmitting(14);

        expect(storeMock.isOperationPending).toHaveBeenNthCalledWith(1, ReviewCommentOperationType.CreateReply, '11');
        expect(storeMock.isOperationPending).toHaveBeenNthCalledWith(2, ReviewCommentOperationType.UpdateComment, '12');
        expect(storeMock.isOperationPending).toHaveBeenNthCalledWith(3, ReviewCommentOperationType.DeleteComment, '13');
        expect(storeMock.isOperationPending).toHaveBeenNthCalledWith(4, ReviewCommentOperationType.ToggleResolved, '14');
    });

    it('should delegate draft operations to the store', () => {
        const location = {
            targetType: CommentThreadLocationType.PROBLEM_STATEMENT,
            lineNumber: 2,
        };

        facade.ensureDraft(location);
        facade.setDraftText(location, 'draft');
        facade.removeDraft(location);

        expect(storeMock.ensureDraft).toHaveBeenCalledWith(location);
        expect(storeMock.setDraftText).toHaveBeenCalledWith(location, 'draft');
        expect(storeMock.removeDraft).toHaveBeenCalledWith(location);
    });

    it('should delegate thread operations to the store', () => {
        const request = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialFilePath: 'src/File.java',
            initialLineNumber: 3,
            initialComment: { contentType: 'USER', text: 'note' },
        } as any;

        facade.submitCreateThread(request);
        facade.createReply(9, { contentType: 'USER', text: 'reply' });
        facade.updateComment(10, { contentType: 'USER', text: 'edit' });
        facade.deleteComment(11);
        facade.toggleResolved(12, true);

        expect(storeMock.submitCreateThread).toHaveBeenCalledWith(request);
        expect(storeMock.createReply).toHaveBeenCalledWith(9, { contentType: 'USER', text: 'reply' });
        expect(storeMock.updateComment).toHaveBeenCalledWith(10, { contentType: 'USER', text: 'edit' });
        expect(storeMock.deleteComment).toHaveBeenCalledWith(11);
        expect(storeMock.toggleResolved).toHaveBeenCalledWith(12, true);
    });
});
