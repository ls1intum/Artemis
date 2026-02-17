import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';
import { ReviewCommentOperationStatus, ReviewCommentOperationType, ReviewCommentStore, buildDraftLocationKey } from 'app/exercise/review/review-comment.store';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';

describe('ReviewCommentStore', () => {
    setupTestBed({ zoneless: true });

    let store: ReviewCommentStore;
    let reviewCommentServiceMock: any;
    let alertServiceMock: any;

    const location = {
        targetType: CommentThreadLocationType.TEMPLATE_REPO,
        lineNumber: 3,
        filePath: 'src/File.java',
    };

    const thread = {
        id: 1,
        exerciseId: 10,
        targetType: CommentThreadLocationType.TEMPLATE_REPO,
        initialLineNumber: 3,
        lineNumber: 3,
        outdated: false,
        resolved: false,
        comments: [
            {
                id: 10,
                threadId: 1,
                type: 'USER',
                content: { contentType: 'USER', text: 'old' },
                createdDate: '2024-01-01T00:00:00Z',
                lastModifiedDate: '2024-01-01T00:00:00Z',
            },
        ],
    } as any;

    beforeEach(() => {
        reviewCommentServiceMock = {
            loadThreads: vi.fn(() => of([])),
            createThread: vi.fn(),
            deleteComment: vi.fn(),
            createUserComment: vi.fn(),
            updateUserCommentContent: vi.fn(),
            updateThreadResolvedState: vi.fn(),
            appendThreadToThreads: vi.fn((threads: any[], newThread: any) => [...threads, newThread]),
            removeCommentFromThreads: vi.fn((threads: any[]) => threads),
            appendCommentToThreads: vi.fn((threads: any[], createdComment: any) =>
                threads.map((existingThread: any) =>
                    existingThread.id === createdComment.threadId ? { ...existingThread, comments: [...(existingThread.comments ?? []), createdComment] } : existingThread,
                ),
            ),
            updateCommentInThreads: vi.fn((threads: any[], updatedComment: any) =>
                threads.map((existingThread: any) => ({
                    ...existingThread,
                    comments: (existingThread.comments ?? []).map((comment: any) => (comment.id === updatedComment.id ? { ...comment, ...updatedComment } : comment)),
                })),
            ),
            replaceThreadInThreads: vi.fn((threads: any[], updatedThread: any) =>
                threads.map((existingThread: any) => (existingThread.id === updatedThread.id ? updatedThread : existingThread)),
            ),
        };
        alertServiceMock = {
            error: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [ReviewCommentStore, { provide: ExerciseReviewCommentService, useValue: reviewCommentServiceMock }, { provide: AlertService, useValue: alertServiceMock }],
        });
        store = TestBed.inject(ReviewCommentStore);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reset state when exercise context changes', () => {
        const changed = store.setExercise(10);
        store.ensureDraft(location);
        store.setDraftText(location, 'draft');
        store.setReplyDraft(1, 'reply');
        store.initializeEditDraft(10, 'edit');

        const unchanged = store.setExercise(10);
        const changedAgain = store.setExercise(11);

        expect(changed).toBe(true);
        expect(unchanged).toBe(false);
        expect(changedAgain).toBe(true);
        expect(store.threads()).toEqual([]);
        expect(store.hasDraft(location)).toBe(false);
        expect(store.getReplyDraft(1)).toBe('');
        expect(store.getEditDraft(10)).toBe('');
    });

    it('should manage location drafts and clear drafts by target type', () => {
        store.setExercise(10);
        const problemStatementLocation = { targetType: CommentThreadLocationType.PROBLEM_STATEMENT, lineNumber: 1 };

        store.ensureDraft(location);
        store.setDraftText(location, 'template draft');
        store.ensureDraft(problemStatementLocation);
        store.setDraftText(problemStatementLocation, 'problem statement draft');

        expect(store.hasDraft(location)).toBe(true);
        expect(store.getDraftText(location)).toBe('template draft');
        expect(store.getDraftText(problemStatementLocation)).toBe('problem statement draft');

        store.clearDraftsForTargetType(CommentThreadLocationType.PROBLEM_STATEMENT);

        expect(store.hasDraft(location)).toBe(true);
        expect(store.hasDraft(problemStatementLocation)).toBe(false);
    });

    it('should load threads and reconcile stale reply and edit drafts', () => {
        store.setExercise(10);
        store.setReplyDraft(1, 'keep me');
        store.setReplyDraft(2, 'drop me');
        store.initializeEditDraft(10, 'keep me');
        store.initializeEditDraft(99, 'drop me');
        reviewCommentServiceMock.loadThreads.mockReturnValue(of([thread]));

        store.reloadThreads();

        expect(reviewCommentServiceMock.loadThreads).toHaveBeenCalledWith(10);
        expect(store.threads()).toEqual([thread]);
        expect(store.getReplyDraft(1)).toBe('keep me');
        expect(store.getReplyDraft(2)).toBe('');
        expect(store.getEditDraft(10)).toBe('keep me');
        expect(store.getEditDraft(99)).toBe('');
        expect(store.pendingOps().at(-1)?.status).toBe(ReviewCommentOperationStatus.Success);
        expect(alertServiceMock.error).not.toHaveBeenCalled();
    });

    it('should track pending operations while create-thread request is in flight', () => {
        store.setExercise(10);
        const request = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialFilePath: location.filePath,
            initialLineNumber: location.lineNumber,
            initialComment: { contentType: 'USER', text: 'note' },
        } as any;
        const requestSubject = new Subject<HttpResponse<any>>();
        reviewCommentServiceMock.createThread.mockReturnValue(requestSubject.asObservable());

        store.submitCreateThread(request);

        expect(store.isOperationPending(ReviewCommentOperationType.CreateThread, buildDraftLocationKey(location))).toBe(true);
        requestSubject.next(new HttpResponse({ body: { ...thread, id: 2, comments: [] } }));
        requestSubject.complete();
        expect(store.isOperationPending(ReviewCommentOperationType.CreateThread, buildDraftLocationKey(location))).toBe(false);
    });

    it('should append created thread and remove the corresponding draft on success', () => {
        store.setExercise(10);
        store.ensureDraft(location);
        store.setDraftText(location, 'note');
        const request = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialFilePath: location.filePath,
            initialLineNumber: location.lineNumber,
            initialComment: { contentType: 'USER', text: 'note' },
        } as any;
        const createdThread = { ...thread, id: 2, comments: [] };
        reviewCommentServiceMock.createThread.mockReturnValue(of(new HttpResponse({ body: createdThread })));

        store.submitCreateThread(request);

        expect(reviewCommentServiceMock.createThread).toHaveBeenCalledWith(10, request);
        expect(reviewCommentServiceMock.appendThreadToThreads).toHaveBeenCalled();
        expect(store.threads()).toEqual([createdThread]);
        expect(store.hasDraft(location)).toBe(false);
        expect(store.pendingOps().at(-1)?.status).toBe(ReviewCommentOperationStatus.Success);
    });

    it('should keep draft and alert on create-thread failure', () => {
        store.setExercise(10);
        store.ensureDraft(location);
        store.setDraftText(location, 'note');
        const request = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialFilePath: location.filePath,
            initialLineNumber: location.lineNumber,
            initialComment: { contentType: 'USER', text: 'note' },
        } as any;
        reviewCommentServiceMock.createThread.mockReturnValue(throwError(() => new Error('boom')));

        store.submitCreateThread(request);

        expect(store.hasDraft(location)).toBe(true);
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.review.saveFailed');
        expect(store.pendingOps().at(-1)?.status).toBe(ReviewCommentOperationStatus.Error);
    });

    it('should delete comment and reconcile related drafts', () => {
        store.setExercise(10);
        reviewCommentServiceMock.loadThreads.mockReturnValue(of([thread]));
        store.reloadThreads();
        store.setReplyDraft(1, 'reply');
        store.initializeEditDraft(10, 'edit');
        reviewCommentServiceMock.removeCommentFromThreads.mockReturnValue([]);
        reviewCommentServiceMock.deleteComment.mockReturnValue(of(new HttpResponse({ status: 200 })));

        store.deleteComment(10);

        expect(reviewCommentServiceMock.deleteComment).toHaveBeenCalledWith(10, 10);
        expect(store.threads()).toEqual([]);
        expect(store.getReplyDraft(1)).toBe('');
        expect(store.getEditDraft(10)).toBe('');
    });

    it('should append reply and clear reply draft on success', () => {
        store.setExercise(10);
        reviewCommentServiceMock.loadThreads.mockReturnValue(of([thread]));
        store.reloadThreads();
        store.setReplyDraft(1, 'reply');
        const createdComment = {
            id: 11,
            threadId: 1,
            type: 'USER',
            content: { contentType: 'USER', text: 'reply' },
            createdDate: '2024-01-02T00:00:00Z',
            lastModifiedDate: '2024-01-02T00:00:00Z',
        } as any;
        reviewCommentServiceMock.createUserComment.mockReturnValue(of(new HttpResponse({ body: createdComment })));

        store.createReply(1, { contentType: 'USER', text: 'reply' });

        expect(reviewCommentServiceMock.createUserComment).toHaveBeenCalledWith(10, 1, { contentType: 'USER', text: 'reply' });
        expect(reviewCommentServiceMock.appendCommentToThreads).toHaveBeenCalled();
        expect(store.getReplyDraft(1)).toBe('');
    });

    it('should update comment and clear edit draft on success', () => {
        store.setExercise(10);
        reviewCommentServiceMock.loadThreads.mockReturnValue(of([thread]));
        store.reloadThreads();
        store.initializeEditDraft(10, 'edit');
        const updatedComment = {
            id: 10,
            threadId: 1,
            type: 'USER',
            content: { contentType: 'USER', text: 'edited' },
            createdDate: '2024-01-01T00:00:00Z',
            lastModifiedDate: '2024-01-02T00:00:00Z',
        } as any;
        reviewCommentServiceMock.updateUserCommentContent.mockReturnValue(of(new HttpResponse({ body: updatedComment })));

        store.updateComment(10, { contentType: 'USER', text: 'edited' });

        expect(reviewCommentServiceMock.updateUserCommentContent).toHaveBeenCalledWith(10, 10, { contentType: 'USER', text: 'edited' });
        expect(reviewCommentServiceMock.updateCommentInThreads).toHaveBeenCalled();
        expect(store.getEditDraft(10)).toBe('');
    });

    it('should replace thread on resolve toggle success', () => {
        store.setExercise(10);
        reviewCommentServiceMock.loadThreads.mockReturnValue(of([thread]));
        store.reloadThreads();
        const resolvedThread = { ...thread, resolved: true };
        reviewCommentServiceMock.updateThreadResolvedState.mockReturnValue(of(new HttpResponse({ body: resolvedThread })));

        store.toggleResolved(1, true);

        expect(reviewCommentServiceMock.updateThreadResolvedState).toHaveBeenCalledWith(10, 1, true);
        expect(reviewCommentServiceMock.replaceThreadInThreads).toHaveBeenCalled();
        expect(store.threads()[0]?.resolved).toBe(true);
    });

    it('should alert on load failure and record failed operation', () => {
        store.setExercise(10);
        reviewCommentServiceMock.loadThreads.mockReturnValue(throwError(() => new Error('boom')));

        store.reloadThreads();

        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.review.loadFailed');
        expect(store.threads()).toEqual([]);
        expect(store.pendingOps().at(-1)?.status).toBe(ReviewCommentOperationStatus.Error);
    });
});
