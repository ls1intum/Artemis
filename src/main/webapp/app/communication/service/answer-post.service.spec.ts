import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { metisAnswerPostToCreateUser1, metisResolvingAnswerPostUser1 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('AnswerPost Service', () => {
    setupTestBed({ zoneless: true });

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
            const returnedFromService = { ...metisAnswerPostToCreateUser1, id: 1 };
            const expected = { ...returnedFromService };
            service
                .create(1, metisAnswerPostToCreateUser1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should update a AnswerPost text field', () => {
            const returnedFromService = { ...metisResolvingAnswerPostUser1, content: 'This is another test answer' };
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
            const returnedFromService = { ...metisResolvingAnswerPostUser1, resolvesPost: true };
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
            service.delete(1, metisResolvingAnswerPostUser1).subscribe((resp) => expect(resp.ok).toBe(true));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            vi.advanceTimersByTime(0);
        });

        it('should get source answer posts by IDs', () => {
            const answerPostIds = [1, 2, 3];
            const returnedFromService = [metisResolvingAnswerPostUser1, { ...metisResolvingAnswerPostUser1, id: 2 }, { ...metisResolvingAnswerPostUser1, id: 3 }];
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
