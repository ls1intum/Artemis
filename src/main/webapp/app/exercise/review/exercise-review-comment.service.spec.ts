import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { Subject } from 'rxjs';
import { ReviewThreadSyncAction, ReviewThreadSyncUpdate } from 'app/exercise/shared/entities/review/review-thread-sync-update.model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ReviewThreadSyncUpdateEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { CommentContentType } from 'app/exercise/shared/entities/review/comment-content.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

describe('ExerciseReviewCommentService', () => {
    setupTestBed({ zoneless: true });
    let service: ExerciseReviewCommentService;
    let httpMock: HttpTestingController;
    let alertServiceMock: { error: ReturnType<typeof vi.fn> };
    let syncServiceMock: { subscribeToUpdates: ReturnType<typeof vi.fn> };
    let syncSubject: Subject<ExerciseEditorSyncEvent>;

    const createReviewSyncEvent = (update: ReviewThreadSyncUpdate): ReviewThreadSyncUpdateEvent => {
        return {
            eventType: ExerciseEditorSyncEventType.REVIEW_THREAD_UPDATE,
            target: ExerciseEditorSyncTarget.REVIEW_COMMENTS,
            action: update.action,
            exerciseId: update.exerciseId,
            thread: update.thread,
            comment: update.comment,
            commentId: update.commentId,
            threadIds: update.threadIds,
            groupId: update.groupId,
        };
    };

    beforeEach(() => {
        alertServiceMock = {
            error: vi.fn(),
        };
        syncSubject = new Subject<ExerciseEditorSyncEvent>();
        syncServiceMock = {
            subscribeToUpdates: vi.fn(() => syncSubject.asObservable()),
        };

        TestBed.configureTestingModule({
            providers: [
                ExerciseReviewCommentService,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: alertServiceMock },
                { provide: ExerciseEditorSyncService, useValue: syncServiceMock },
            ],
        });

        service = TestBed.inject(ExerciseReviewCommentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('setExercise should clear thread state when exercise id changes', () => {
        service.threads.set([{ id: 1 } as any]);
        service.selectedFeedbackThreadIds.set([1]);

        const changed = service.setExercise(42);

        expect(changed).toBe(true);
        expect(service.threads()).toEqual([]);
        expect(service.selectedFeedbackThreadIds()).toEqual([]);
        expect(syncServiceMock.subscribeToUpdates).toHaveBeenCalledTimes(1);
    });

    it('setExercise should not throw when synchronization connection is not ready', () => {
        syncServiceMock.subscribeToUpdates.mockImplementationOnce(() => {
            throw new Error('not connected');
        });

        const changed = service.setExercise(42);

        expect(changed).toBe(true);
        expect(service.threads()).toEqual([]);
    });

    it('setExercise should not clear thread state when exercise id is unchanged', () => {
        service.setExercise(42);
        service.threads.set([{ id: 1 } as any]);

        const changed = service.setExercise(42);

        expect(changed).toBe(false);
        expect(service.threads()).toEqual([{ id: 1 } as any]);
        expect(syncServiceMock.subscribeToUpdates).toHaveBeenCalledTimes(1);
    });

    it('reloadThreads should clear state when no active exercise exists', () => {
        service.threads.set([{ id: 1 } as any]);

        service.reloadThreads();

        expect(service.threads()).toEqual([]);
    });

    it('reloadThreads should load and store threads for active exercise', () => {
        service.setExercise(2);

        service.reloadThreads();

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        expect(req.request.method).toBe('GET');
        req.flush([{ id: 11 }]);

        expect(service.threads()).toEqual([{ id: 11 } as any]);
        expect(syncServiceMock.subscribeToUpdates).toHaveBeenCalledTimes(1);
    });

    it('reloadThreads should clear state and show alert on failure', () => {
        service.setExercise(2);
        service.threads.set([{ id: 99 } as any]);

        service.reloadThreads();

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        req.flush('failed', { status: 500, statusText: 'Server Error' });

        expect(service.threads()).toEqual([]);
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.review.loadFailed');
        expect(syncServiceMock.subscribeToUpdates).toHaveBeenCalledTimes(1);
    });

    it('reloadThreads should ignore stale success responses after exercise switch', () => {
        service.setExercise(1);

        service.reloadThreads();
        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');

        service.setExercise(2);
        req.flush([{ id: 11 }]);

        expect(service.threads()).toEqual([]);
    });

    it('reloadThreads should ignore stale error responses after exercise switch', () => {
        service.setExercise(1);

        service.reloadThreads();
        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');

        service.setExercise(2);
        req.flush('failed', { status: 500, statusText: 'Server Error' });

        expect(service.threads()).toEqual([]);
        expect(alertServiceMock.error).not.toHaveBeenCalled();
    });

    it('setExercise should apply synchronization events before the first reload', () => {
        service.setExercise(4);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.THREAD_CREATED,
                exerciseId: 4,
                thread: { id: 2, comments: [] } as any,
            }),
        );

        expect(service.threads()).toEqual([{ id: 2, comments: [] }] as any);
    });

    it('reloadThreads should merge synchronization updates received during an in-flight reload', () => {
        service.setExercise(4);

        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([{ id: 1, comments: [] }]);

        service.reloadThreads();
        const req = httpMock.expectOne('api/exercise/exercises/4/review-threads');

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.THREAD_CREATED,
                exerciseId: 4,
                thread: { id: 2, comments: [] } as any,
            }),
        );

        req.flush([{ id: 1, comments: [] }]);

        expect(service.threads()).toEqual([
            { id: 1, comments: [] },
            { id: 2, comments: [] },
        ] as any);
    });

    it('reloadThreads should preserve synchronization deletions received during an in-flight reload', () => {
        service.setExercise(4);

        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([{ id: 1, comments: [{ id: 9 }] }]);

        service.reloadThreads();
        const req = httpMock.expectOne('api/exercise/exercises/4/review-threads');

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.COMMENT_DELETED,
                exerciseId: 4,
                commentId: 9,
            }),
        );

        req.flush([{ id: 1, comments: [{ id: 9 }] }]);

        expect(service.threads()).toEqual([]);
    });

    it('createThreadInContext should be ignored without active exercise', () => {
        service.createThreadInContext({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any);

        expect(httpMock.match(() => true)).toHaveLength(0);
    });

    it('createThreadInContext should append created thread', () => {
        service.setExercise(1);
        service.threads.set([{ id: 1, comments: [] } as any]);
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any;

        service.createThreadInContext(payload);

        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush({ id: 2, comments: [] });

        expect(service.threads()).toEqual([
            { id: 1, comments: [] },
            { id: 2, comments: [] },
        ] as any);
    });

    it('createThreadInContext should invoke success callback only after persistence', () => {
        service.setExercise(1);
        const onSuccess = vi.fn();
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any;

        service.createThreadInContext(payload, onSuccess);
        expect(onSuccess).not.toHaveBeenCalled();

        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');
        req.flush({ id: 2, comments: [] });

        expect(onSuccess).toHaveBeenCalledOnce();
    });

    it('createThreadInContext should not invoke success callback on failure', () => {
        service.setExercise(1);
        const onSuccess = vi.fn();
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any;

        service.createThreadInContext(payload, onSuccess);

        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');
        req.flush('failed', { status: 500, statusText: 'Server Error' });

        expect(onSuccess).not.toHaveBeenCalled();
    });

    it('createThreadInContext should ignore stale success responses after exercise switch', () => {
        service.setExercise(1);
        const onSuccess = vi.fn();
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any;

        service.createThreadInContext(payload, onSuccess);
        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');

        service.setExercise(2);
        req.flush({ id: 2, comments: [] });

        expect(service.threads()).toEqual([]);
        expect(onSuccess).not.toHaveBeenCalled();
    });

    it('deleteCommentInContext should remove comments and empty threads', () => {
        service.setExercise(1);
        service.threads.set([
            { id: 1, comments: [{ id: 7 }] },
            { id: 2, comments: [{ id: 8 }] },
        ] as any);

        service.deleteCommentInContext(7);

        const req = httpMock.expectOne('api/exercise/exercises/1/review-comments/7');
        expect(req.request.method).toBe('DELETE');
        req.flush({});

        expect(service.threads()).toEqual([{ id: 2, comments: [{ id: 8 }] }] as any);
    });

    it('createReplyInContext should append reply to the matching thread', () => {
        service.setExercise(3);
        service.threads.set([{ id: 5, comments: [] }] as any);

        service.createReplyInContext(5, { contentType: CommentContentType.USER, text: 'reply' } as any);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        expect(req.request.method).toBe('POST');
        req.flush({ id: 10, threadId: 5 });

        expect(service.threads()).toEqual([{ id: 5, comments: [{ id: 10, threadId: 5 }] }] as any);
    });

    it('createReplyInContext should invoke success callback only after persistence', () => {
        service.setExercise(3);
        const onSuccess = vi.fn();

        service.createReplyInContext(5, { contentType: CommentContentType.USER, text: 'reply' } as any, onSuccess);
        expect(onSuccess).not.toHaveBeenCalled();

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        req.flush({ id: 10, threadId: 5 });

        expect(onSuccess).toHaveBeenCalledOnce();
    });

    it('updateCommentInContext should replace comment content in-place', () => {
        service.setExercise(3);
        service.threads.set([{ id: 5, comments: [{ id: 10, threadId: 5, content: { text: 'old' } }] }] as any);
        const content = { contentType: CommentContentType.USER, text: 'new' } as any;

        service.updateCommentInContext(10, content);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-comments/10');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(content);
        req.flush({ id: 10, threadId: 5, content: { text: 'new' } });

        expect(service.threads()).toEqual([{ id: 5, comments: [{ id: 10, threadId: 5, content: { text: 'new' } }] }] as any);
    });

    it('updateCommentInContext should invoke success callback only after persistence', () => {
        service.setExercise(3);
        const content = { contentType: CommentContentType.USER, text: 'new' } as any;
        const onSuccess = vi.fn();

        service.updateCommentInContext(10, content, onSuccess);
        expect(onSuccess).not.toHaveBeenCalled();

        const req = httpMock.expectOne('api/exercise/exercises/3/review-comments/10');
        req.flush({ id: 10, threadId: 5, content: { text: 'new' } });

        expect(onSuccess).toHaveBeenCalledOnce();
    });

    it('toggleResolvedInContext should replace updated thread', () => {
        service.setExercise(3);
        service.threads.set([{ id: 7, resolved: false }] as any);
        service.selectedFeedbackThreadIds.set([7]);

        service.toggleResolvedInContext(7, true);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/7/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush({ id: 7, resolved: true });

        expect(service.threads()).toEqual([{ id: 7, resolved: true }] as any);
        expect(service.selectedFeedbackThreadIds()).toEqual([]);
    });

    it('toggleThreadFeedbackSelection should add and remove the selected thread id', () => {
        service.toggleThreadFeedbackSelection(5);
        expect(service.selectedFeedbackThreadIds()).toEqual([5]);

        service.toggleThreadFeedbackSelection(5);
        expect(service.selectedFeedbackThreadIds()).toEqual([]);
    });

    it('getSelectedFeedbackThreadIdsForRepository should keep only active matching threads in selection order', () => {
        service.threads.set([
            { id: 3, targetType: CommentThreadLocationType.SOLUTION_REPO, resolved: false, outdated: false },
            { id: 1, targetType: CommentThreadLocationType.TEMPLATE_REPO, resolved: false, outdated: false },
            { id: 2, targetType: CommentThreadLocationType.TEMPLATE_REPO, resolved: true, outdated: false },
        ] as any);
        service.selectedFeedbackThreadIds.set([2, 1, 3]);

        const threadIds = service.getSelectedFeedbackThreadIdsForRepository(RepositoryType.TEMPLATE);

        expect(threadIds).toEqual([1]);
    });

    it('toggleGroupResolvedInContext should replace all updated group threads', () => {
        service.setExercise(3);
        service.threads.set([
            { id: 7, groupId: 50, resolved: false },
            { id: 8, groupId: 50, resolved: false },
            { id: 9, resolved: false },
        ] as any);

        service.toggleGroupResolvedInContext(50, true);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-thread-groups/50/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush([
            { id: 7, groupId: 50, resolved: true },
            { id: 8, groupId: 50, resolved: true },
        ]);

        expect(service.threads()).toEqual([
            { id: 7, groupId: 50, resolved: true },
            { id: 8, groupId: 50, resolved: true },
            { id: 9, resolved: false },
        ] as any);
    });

    it('markInlineFixAppliedInContext should update matching consistency comment', () => {
        service.setExercise(3);
        service.threads.set([
            {
                id: 7,
                comments: [
                    {
                        id: 12,
                        threadId: 7,
                        content: {
                            contentType: CommentContentType.CONSISTENCY_CHECK,
                            text: 'issue',
                            suggestedFix: { applied: false },
                        },
                    },
                ],
            },
        ] as any);

        service.markInlineFixAppliedInContext(12);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-comments/12/inline-fix/applied');
        expect(req.request.method).toBe('PUT');
        req.flush({
            id: 12,
            threadId: 7,
            content: {
                contentType: CommentContentType.CONSISTENCY_CHECK,
                text: 'issue',
                suggestedFix: { applied: true },
            },
        });

        expect((service.threads() as any)[0].comments[0].content.suggestedFix.applied).toBe(true);
    });

    it('createThread should send POST request', () => {
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: CommentContentType.USER, text: 'hi' },
        } as any;

        service.createThread(1, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush({});
    });

    it('getThreads should send GET request', () => {
        service.getThreads(2).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('loadThreads should map response body to thread array', () => {
        let threads: any[] = [];
        service.loadThreads(2).subscribe((result) => (threads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        req.flush([{ id: 11 }]);

        expect(threads).toHaveLength(1);
        expect(threads[0].id).toBe(11);
    });

    it('createUserComment should send POST request', () => {
        const payload = { contentType: CommentContentType.USER, text: 'reply' } as any;

        service.createUserComment(3, 5, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush({});
    });

    it('updateThreadResolvedState should send PUT request', () => {
        service.updateThreadResolvedState(4, 7, true).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/4/review-threads/7/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush({});
    });

    it('updateThreadGroupResolvedState should send PUT request', () => {
        service.updateThreadGroupResolvedState(4, 15, true).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/4/review-thread-groups/15/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush([]);
    });

    it('updateUserCommentContent should send PUT request', () => {
        const payload = { contentType: CommentContentType.USER, text: 'update' } as any;

        service.updateUserCommentContent(8, 9, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/8/review-comments/9');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(payload);
        req.flush({});
    });

    it('markConsistencyInlineFixApplied should send PUT request', () => {
        service.markConsistencyInlineFixApplied(8, 9).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/8/review-comments/9/inline-fix/applied');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({});
        req.flush({});
    });

    it('deleteComment should send DELETE request', () => {
        service.deleteComment(10, 11).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/10/review-comments/11');
        expect(req.request.method).toBe('DELETE');
        req.flush({});
    });

    it('removeCommentFromThreads should remove comment and drop empty threads', () => {
        const threads = [{ id: 1, comments: [{ id: 5 }, { id: 6 }] }, { id: 2, comments: [{ id: 7 }] }, { id: 3 }] as any;

        const result = service.removeCommentFromThreads(threads, 7);

        expect(result).toHaveLength(2);
        expect(result.find((t: any) => t.id === 2)).toBeUndefined();
        expect(result.find((t: any) => t.id === 1)?.comments).toHaveLength(2);
    });

    it('appendCommentToThreads should append to matching thread', () => {
        const threads = [
            { id: 1, comments: [] },
            { id: 2, comments: [{ id: 9 }] },
        ] as any;
        const created = { id: 10, threadId: 2 } as any;

        const result = service.appendCommentToThreads(threads, created);

        expect(result.find((t: any) => t.id === 2)?.comments).toHaveLength(2);
    });

    it('updateCommentInThreads should update matching comment', () => {
        const threads = [{ id: 1, comments: [{ id: 5, content: { text: 'old' } }] }] as any;
        const updated = { id: 5, threadId: 1, content: { text: 'new' } } as any;

        const result = service.updateCommentInThreads(threads, updated);

        expect(result[0]?.comments?.[0]?.content?.text).toBe('new');
    });

    it('replaceThreadInThreads should replace updated thread', () => {
        const threads = [
            { id: 1, resolved: false },
            { id: 2, resolved: false },
        ] as any;
        const updatedThread = { id: 2, resolved: true } as any;

        const result = service.replaceThreadInThreads(threads, updatedThread);

        expect(result.find((t: any) => t.id === 2)?.resolved).toBe(true);
    });

    it('replaceThreadsInThreads should replace matching threads and keep others', () => {
        const threads = [
            { id: 1, resolved: false },
            { id: 2, resolved: false },
            { id: 3, resolved: false },
        ] as any;
        const updatedThreads = [
            { id: 1, resolved: true },
            { id: 3, resolved: true },
        ] as any;

        const result = service.replaceThreadsInThreads(threads, updatedThreads);

        expect(result).toEqual([
            { id: 1, resolved: true },
            { id: 2, resolved: false },
            { id: 3, resolved: true },
        ] as any);
    });

    it('appendThreadToThreads should append new thread', () => {
        const threads = [{ id: 1 }] as any;
        const newThread = { id: 2 } as any;

        const result = service.appendThreadToThreads(threads, newThread);

        expect(result).toHaveLength(2);
    });

    it('should apply THREAD_CREATED synchronization update idempotently', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([{ id: 1, comments: [] }] as any);

        syncSubject.next(createReviewSyncEvent({ action: ReviewThreadSyncAction.THREAD_CREATED, exerciseId: 4, thread: { id: 2, comments: [] } as any }));
        syncSubject.next(createReviewSyncEvent({ action: ReviewThreadSyncAction.THREAD_CREATED, exerciseId: 4, thread: { id: 2, comments: [] } as any }));

        expect(service.threads()).toEqual([
            { id: 1, comments: [] },
            { id: 2, comments: [] },
        ] as any);
    });

    it('should apply COMMENT_CREATED synchronization update idempotently', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([{ id: 1, comments: [] }] as any);

        const comment = { id: 9, threadId: 1, content: { text: 'Hello' } } as any;
        syncSubject.next(createReviewSyncEvent({ action: ReviewThreadSyncAction.COMMENT_CREATED, exerciseId: 4, comment }));
        syncSubject.next(createReviewSyncEvent({ action: ReviewThreadSyncAction.COMMENT_CREATED, exerciseId: 4, comment }));

        expect(service.threads()).toEqual([{ id: 1, comments: [comment] }] as any);
    });

    it('should apply COMMENT_UPDATED synchronization update', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([{ id: 1, comments: [{ id: 9, threadId: 1, content: { text: 'Old' } }] }] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.COMMENT_UPDATED,
                exerciseId: 4,
                comment: { id: 9, threadId: 1, content: { text: 'New' } } as any,
            }),
        );

        expect(service.threads()).toEqual([{ id: 1, comments: [{ id: 9, threadId: 1, content: { text: 'New' } }] }] as any);
    });

    it('should preserve local comments when THREAD_UPDATED synchronization payload is stale', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([
            { id: 1, resolved: false, comments: [{ id: 11, threadId: 1, content: { text: 'new comment' }, lastModifiedDate: '2026-01-01T10:00:00.000Z' }] },
        ] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.THREAD_UPDATED,
                exerciseId: 4,
                thread: { id: 1, resolved: true, comments: [] } as any,
            }),
        );

        expect(service.threads()).toEqual([
            { id: 1, resolved: true, comments: [{ id: 11, threadId: 1, content: { text: 'new comment' }, lastModifiedDate: '2026-01-01T10:00:00.000Z' }] },
        ] as any);
    });

    it('should keep the most recent comment version when THREAD_UPDATED contains duplicates', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([
            {
                id: 1,
                comments: [{ id: 11, threadId: 1, content: { text: 'newer local' }, lastModifiedDate: '2026-01-01T10:00:00.000Z' }],
            },
        ] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.THREAD_UPDATED,
                exerciseId: 4,
                thread: {
                    id: 1,
                    comments: [{ id: 11, threadId: 1, content: { text: 'older incoming' }, lastModifiedDate: '2026-01-01T09:00:00.000Z' }],
                } as any,
            }),
        );

        expect(service.threads()).toEqual([
            {
                id: 1,
                comments: [{ id: 11, threadId: 1, content: { text: 'newer local' }, lastModifiedDate: '2026-01-01T10:00:00.000Z' }],
            },
        ] as any);
    });

    it('should apply COMMENT_DELETED synchronization update and drop empty thread', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([{ id: 1, comments: [{ id: 9 }] }] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.COMMENT_DELETED,
                exerciseId: 4,
                commentId: 9,
            }),
        );

        expect(service.threads()).toEqual([]);
    });

    it('should apply GROUP_UPDATED synchronization update', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([
            { id: 1, groupId: undefined },
            { id: 2, groupId: undefined },
            { id: 3, groupId: 8 },
        ] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.GROUP_UPDATED,
                exerciseId: 4,
                threadIds: [1, 2],
                groupId: 77,
            }),
        );

        expect(service.threads()).toEqual([
            { id: 1, groupId: 77 },
            { id: 2, groupId: 77 },
            { id: 3, groupId: 8 },
        ] as any);
    });

    it('should ignore synchronization events from other exercises', () => {
        service.setExercise(4);
        service.reloadThreads();
        httpMock.expectOne('api/exercise/exercises/4/review-threads').flush([]);
        service.threads.set([{ id: 1, comments: [] }] as any);

        syncSubject.next(
            createReviewSyncEvent({
                action: ReviewThreadSyncAction.THREAD_CREATED,
                exerciseId: 5,
                thread: { id: 2, comments: [] } as any,
            }),
        );

        expect(service.threads()).toEqual([{ id: 1, comments: [] }] as any);
    });
});
