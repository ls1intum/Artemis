import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { communicationAnswerPostToCreateUser1, communicationResolvingAnswerPostUser1 } from 'test/helpers/sample/communication-sample-data';
import { provideHttpClient } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';

describe('AnswerPost Service', () => {
    let service: AnswerPostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(AnswerPostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create a AnswerPost', () => {
            const returnedFromService = { ...communicationAnswerPostToCreateUser1, id: 1 };
            const expected = { ...returnedFromService };
            service
                .create(1, communicationAnswerPostToCreateUser1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should update a AnswerPost text field', () => {
            const returnedFromService = { ...communicationResolvingAnswerPostUser1, content: 'This is another test answer' };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should update a AnswerPost resolvesPost field', () => {
            const returnedFromService = { ...communicationResolvingAnswerPostUser1, resolvesPost: true };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should delete a AnswerPost', () => {
            service.delete(1, communicationResolvingAnswerPostUser1).subscribe((resp) => expect(resp.ok).toBe(true));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            vi.advanceTimersByTime(0);
        });

        it('keeps a denied source request available to its inline error handler', () => {
            const onError = vi.fn();
            service.getSourceAnswerPostsByIds(1, [2]).subscribe({ error: onError });
            const req = httpMock.expectOne('api/communication/courses/1/answer-messages-source-posts?answerPostIds=2');
            expect(req.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);

            req.flush({ title: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

            expect(onError).toHaveBeenCalledOnce();
            expect(onError.mock.calls[0][0].status).toBe(403);
        });

        it('allows global alerts for a denied verification action', () => {
            const onError = vi.fn();
            service.verify(1, 2).subscribe({ error: onError });
            const req = httpMock.expectOne('api/communication/courses/1/answer-messages/2/verify');
            expect(req.request.method).toBe('PATCH');
            expect(req.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(false);

            req.flush({ title: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

            expect(onError).toHaveBeenCalledOnce();
            expect(onError.mock.calls[0][0].status).toBe(403);
        });

        it('should get source answer posts by IDs', () => {
            const answerPostIds = [1, 2, 3];
            const returnedFromService = [
                communicationResolvingAnswerPostUser1,
                { ...communicationResolvingAnswerPostUser1, id: 2 },
                { ...communicationResolvingAnswerPostUser1, id: 3 },
            ];
            const expected = returnedFromService;

            service
                .getSourceAnswerPostsByIds(1, answerPostIds)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({
                method: 'GET',
                url: `api/communication/courses/1/answer-messages-source-posts?answerPostIds=${answerPostIds.join(',')}`,
            });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });
    });
});
