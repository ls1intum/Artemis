import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExerciseReviewCommentService', () => {
    setupTestBed({ zoneless: true });
    let service: ExerciseReviewCommentService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ExerciseReviewCommentService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(ExerciseReviewCommentService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should create a thread', () => {
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

    it('should create a thread with initial comment and return updated threads', () => {
        const existingThreads = [{ id: 1, comments: [{ id: 1 }] }] as any;
        let resultThreads: any[] = [];

        service
            .createThreadWithInitialComment(1, existingThreads, {
                targetType: CommentThreadLocationType.TEMPLATE_REPO,
                initialFilePath: 'file.java',
                initialLineNumber: 2,
                initialComment: { contentType: 'USER', text: 'hello' },
            })
            .subscribe((threads) => (resultThreads = threads as any));

        const req = httpMock.expectOne('api/exercise/exercises/1/review-threads');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({
            targetType: CommentThreadLocationType.TEMPLATE_REPO,
            initialFilePath: 'file.java',
            initialLineNumber: 2,
            initialComment: { contentType: 'USER', text: 'hello' },
        });
        req.flush({ id: 2, comments: [] });

        expect(resultThreads).toHaveLength(2);
        expect(resultThreads[1].id).toBe(2);
    });

    it('should get threads', () => {
        service.getThreads(2).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        expect(req.request.method).toBe('GET');
        req.flush([]);
    });

    it('should load threads from response body', () => {
        let threads: any[] = [];
        service.loadThreads(2).subscribe((result) => (threads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/2/review-threads');
        expect(req.request.method).toBe('GET');
        req.flush([{ id: 11 }]);

        expect(threads).toHaveLength(1);
        expect(threads[0].id).toBe(11);
    });

    it('should create a user comment', () => {
        const payload = { contentType: 'USER', text: 'reply' } as any;

        service.createUserComment(3, 5, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(payload);
        req.flush({});
    });

    it('should add reply to existing thread', () => {
        const threads = [{ id: 5, comments: [{ id: 1 }] }] as any;
        let updatedThreads: any[] = [];

        service.addReplyToThread(3, threads, 5, { contentType: 'USER', text: 'reply' }).subscribe((result) => (updatedThreads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/3/review-threads/5/comments');
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual({ contentType: 'USER', text: 'reply' });
        req.flush({ id: 2, threadId: 5 });

        expect(updatedThreads[0].comments).toHaveLength(2);
    });

    it('should update resolved state', () => {
        service.updateThreadResolvedState(4, 7, true).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/4/review-threads/7/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush({});
    });

    it('should update resolved state in local threads', () => {
        const threads = [{ id: 7, resolved: false }] as any;
        let updatedThreads: any[] = [];

        service.updateResolvedStateInThreads(4, threads, 7, true).subscribe((result) => (updatedThreads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/4/review-threads/7/resolved');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ resolved: true });
        req.flush({ id: 7, resolved: true });

        expect(updatedThreads[0].resolved).toBe(true);
    });

    it('should update comment content', () => {
        const payload = { contentType: 'USER', text: 'update' } as any;

        service.updateUserCommentContent(8, 9, payload).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/8/review-comments/9');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(payload);
        req.flush({});
    });

    it('should update user comment in local threads', () => {
        const threads = [{ id: 9, comments: [{ id: 3, content: { text: 'old' } }] }] as any;
        let updatedThreads: any[] = [];

        service.updateUserCommentInThreads(8, threads, 3, { contentType: 'USER', text: 'updated' }).subscribe((result) => (updatedThreads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/8/review-comments/3');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ contentType: 'USER', text: 'updated' });
        req.flush({ id: 3, threadId: 9, content: { text: 'updated' } });

        expect(updatedThreads[0].comments[0].content.text).toBe('updated');
    });

    it('should delete a comment', () => {
        service.deleteComment(10, 11).subscribe();

        const req = httpMock.expectOne('api/exercise/exercises/10/review-comments/11');
        expect(req.request.method).toBe('DELETE');
        req.flush(null);
    });

    it('should delete comment and update local threads', () => {
        const threads = [{ id: 1, comments: [{ id: 11 }] }] as any;
        let updatedThreads: any[] = [];

        service.deleteCommentFromThreads(10, threads, 11).subscribe((result) => (updatedThreads = result as any[]));

        const req = httpMock.expectOne('api/exercise/exercises/10/review-comments/11');
        expect(req.request.method).toBe('DELETE');
        req.flush(null);

        expect(updatedThreads).toHaveLength(0);
    });

    it('should remove a comment from threads and drop empty threads', () => {
        const threads = [{ id: 1, comments: [{ id: 5 }, { id: 6 }] }, { id: 2, comments: [{ id: 7 }] }, { id: 3 }] as any;

        const result = service.removeCommentFromThreads(threads, 7);

        expect(result).toHaveLength(2);
        expect(result.find((t: any) => t.id === 2)).toBeUndefined();
        const remainingThread = result.find((t: any) => t.id === 1);
        expect(remainingThread).toBeDefined();
        expect(remainingThread!.comments).toHaveLength(2);
    });

    it('should append a created comment to its thread', () => {
        const threads = [
            { id: 1, comments: [] },
            { id: 2, comments: [{ id: 9 }] },
        ] as any;
        const created = { id: 10, threadId: 2 } as any;

        const result = service.appendCommentToThreads(threads, created);

        const thread = result.find((t: any) => t.id === 2);
        expect(thread).toBeDefined();
        expect(thread!.comments).toHaveLength(2);
    });

    it('should update a comment in its thread', () => {
        const threads = [{ id: 1, comments: [{ id: 5, content: { text: 'old' } }] }] as any;
        const updated = { id: 5, threadId: 1, content: { text: 'new' } } as any;

        const result = service.updateCommentInThreads(threads, updated);

        const updatedComment = result[0]?.comments?.[0];
        expect(updatedComment).toBeDefined();
        expect(updatedComment!.content.text).toBe('new');
    });

    it('should replace an updated thread', () => {
        const threads = [
            { id: 1, resolved: false },
            { id: 2, resolved: false },
        ] as any;
        const updatedThread = { id: 2, resolved: true } as any;

        const result = service.replaceThreadInThreads(threads, updatedThread);

        const updated = result.find((t: any) => t.id === 2);
        expect(updated).toBeDefined();
        expect(updated!.resolved).toBe(true);
    });

    it('should append a new thread', () => {
        const threads = [{ id: 1 }] as any;
        const newThread = { id: 2 } as any;

        const result = service.appendThreadToThreads(threads, newThread);

        expect(result).toHaveLength(2);
    });
});
