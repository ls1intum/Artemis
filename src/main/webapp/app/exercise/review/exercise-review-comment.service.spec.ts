import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { Subject } from 'rxjs';
import { ReviewThreadWebsocketAction, ReviewThreadWebsocketUpdate } from 'app/exercise/shared/entities/review/review-thread-websocket-update.model';

describe('ExerciseReviewCommentService', () => {
    setupTestBed({ zoneless: true });
    let service: ExerciseReviewCommentService;
    let httpMock: HttpTestingController;
    let alertServiceMock: { error: ReturnType<typeof vi.fn> };
    let websocketServiceMock: { subscribe: ReturnType<typeof vi.fn> };
    let websocketSubject: Subject<ReviewThreadWebsocketUpdate>;

    beforeEach(() => {
        alertServiceMock = {
            error: vi.fn(),
        };
        websocketSubject = new Subject<ReviewThreadWebsocketUpdate>();
        websocketServiceMock = {
            subscribe: vi.fn(() => websocketSubject.asObservable()),
        };

        TestBed.configureTestingModule({
            providers: [
                ExerciseReviewCommentService,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: alertServiceMock },
                { provide: WebsocketService, useValue: websocketServiceMock },
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

        const changed = service.setExercise(42);

        expect(changed).toBe(true);
        expect(service.threads()).toEqual([]);
        expect(websocketServiceMock.subscribe).toHaveBeenCalledWith('/topic/exercises/42/review-threads');
    });

    it('setExercise should not clear thread state when exercise id is unchanged', () => {
        service.setExercise(42);
        service.threads.set([{ id: 1 } as any]);

        const changed = service.setExercise(42);

        expect(changed).toBe(false);
        expect(service.threads()).toEqual([{ id: 1 } as any]);
        expect(websocketServiceMock.subscribe).toHaveBeenCalledTimes(1);
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
    });

    it('reloadThreads should clear state and show alert on failure', () => {
        service.setExercise(2);
        service.threads.set([{ id: 99 } as any]);

        service.reloadThreads();

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        req.flush('failed', { status: 500, statusText: 'Server Error' });

        expect(service.threads()).toEqual([]);
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.review.loadFailed');
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

    it('createThreadInContext should be ignored without active exercise', () => {
        service.createThreadInContext({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: 'USER', text: 'hi' },
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
            initialComment: { contentType: 'USER', text: 'hi' },
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
            initialComment: { contentType: 'USER', text: 'hi' },
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
            initialComment: { contentType: 'USER', text: 'hi' },
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
            initialComment: { contentType: 'USER', text: 'hi' },
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

        service.createReplyInContext(5, { contentType: 'USER', text: 'reply' } as any);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        expect(req.request.method).toBe('POST');
        req.flush({ id: 10, threadId: 5 });

        expect(service.threads()).toEqual([{ id: 5, comments: [{ id: 10, threadId: 5 }] }] as any);
    });

    it('createReplyInContext should invoke success callback only after persistence', () => {
        service.setExercise(3);
        const onSuccess = vi.fn();

        service.createReplyInContext(5, { contentType: 'USER', text: 'reply' } as any, onSuccess);
        expect(onSuccess).not.toHaveBeenCalled();

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        req.flush({ id: 10, threadId: 5 });

        expect(onSuccess).toHaveBeenCalledOnce();
    });

    it('updateCommentInContext should replace comment content in-place', () => {
        service.setExercise(3);
        service.threads.set([{ id: 5, comments: [{ id: 10, threadId: 5, content: { text: 'old' } }] }] as any);
        const content = { contentType: 'USER', text: 'new' } as any;

        service.updateCommentInContext(10, content);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-comments/10');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(content);
        req.flush({ id: 10, threadId: 5, content: { text: 'new' } });

        expect(service.threads()).toEqual([{ id: 5, comments: [{ id: 10, threadId: 5, content: { text: 'new' } }] }] as any);
    });

    it('updateCommentInContext should invoke success callback only after persistence', () => {
        service.setExercise(3);
        const content = { contentType: 'USER', text: 'new' } as any;
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

        service.toggleResolvedInContext(7, true);

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/7/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush({ id: 7, resolved: true });

        expect(service.threads()).toEqual([{ id: 7, resolved: true }] as any);
    });

    it('createThread should send POST request', () => {
        const payload = {
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialLineNumber: 1,
            initialFilePath: 'file.java',
            initialComment: { contentType: 'USER', text: 'hi' },
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
        const payload = { contentType: 'USER', text: 'reply' } as any;

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

    it('updateUserCommentContent should send PUT request', () => {
        const payload = { contentType: 'USER', text: 'update' } as any;

        service.updateUserCommentContent(8, 9, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/8/review-comments/9');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(payload);
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

    it('appendThreadToThreads should append new thread', () => {
        const threads = [{ id: 1 }] as any;
        const newThread = { id: 2 } as any;

        const result = service.appendThreadToThreads(threads, newThread);

        expect(result).toHaveLength(2);
    });

    it('should apply THREAD_CREATED websocket update idempotently', () => {
        service.setExercise(4);
        service.threads.set([{ id: 1, comments: [] }] as any);

        websocketSubject.next({ action: ReviewThreadWebsocketAction.THREAD_CREATED, exerciseId: 4, thread: { id: 2, comments: [] } as any });
        websocketSubject.next({ action: ReviewThreadWebsocketAction.THREAD_CREATED, exerciseId: 4, thread: { id: 2, comments: [] } as any });

        expect(service.threads()).toEqual([
            { id: 1, comments: [] },
            { id: 2, comments: [] },
        ] as any);
    });

    it('should apply COMMENT_CREATED websocket update idempotently', () => {
        service.setExercise(4);
        service.threads.set([{ id: 1, comments: [] }] as any);

        const comment = { id: 9, threadId: 1, content: { text: 'Hello' } } as any;
        websocketSubject.next({ action: ReviewThreadWebsocketAction.COMMENT_CREATED, exerciseId: 4, comment });
        websocketSubject.next({ action: ReviewThreadWebsocketAction.COMMENT_CREATED, exerciseId: 4, comment });

        expect(service.threads()).toEqual([{ id: 1, comments: [comment] }] as any);
    });

    it('should apply COMMENT_UPDATED websocket update', () => {
        service.setExercise(4);
        service.threads.set([{ id: 1, comments: [{ id: 9, threadId: 1, content: { text: 'Old' } }] }] as any);

        websocketSubject.next({
            action: ReviewThreadWebsocketAction.COMMENT_UPDATED,
            exerciseId: 4,
            comment: { id: 9, threadId: 1, content: { text: 'New' } } as any,
        });

        expect(service.threads()).toEqual([{ id: 1, comments: [{ id: 9, threadId: 1, content: { text: 'New' } }] }] as any);
    });

    it('should apply COMMENT_DELETED websocket update and drop empty thread', () => {
        service.setExercise(4);
        service.threads.set([{ id: 1, comments: [{ id: 9 }] }] as any);

        websocketSubject.next({
            action: ReviewThreadWebsocketAction.COMMENT_DELETED,
            exerciseId: 4,
            commentId: 9,
        });

        expect(service.threads()).toEqual([]);
    });

    it('should apply GROUP_UPDATED websocket update', () => {
        service.setExercise(4);
        service.threads.set([
            { id: 1, groupId: undefined },
            { id: 2, groupId: undefined },
            { id: 3, groupId: 8 },
        ] as any);

        websocketSubject.next({
            action: ReviewThreadWebsocketAction.GROUP_UPDATED,
            exerciseId: 4,
            threadIds: [1, 2],
            groupId: 77,
        });

        expect(service.threads()).toEqual([
            { id: 1, groupId: 77 },
            { id: 2, groupId: 77 },
            { id: 3, groupId: 8 },
        ] as any);
    });

    it('should ignore websocket events from other exercises', () => {
        service.setExercise(4);
        service.threads.set([{ id: 1, comments: [] }] as any);

        websocketSubject.next({
            action: ReviewThreadWebsocketAction.THREAD_CREATED,
            exerciseId: 5,
            thread: { id: 2, comments: [] } as any,
        });

        expect(service.threads()).toEqual([{ id: 1, comments: [] }] as any);
    });
});
