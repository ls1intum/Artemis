import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { metisAnswerPostToCreateUser1, metisResolvingAnswerPostUser1 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('AnswerPost Service', () => {
    let service: AnswerPostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(AnswerPostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a AnswerPost', fakeAsync(() => {
            const returnedFromService = Object.assign({}, metisAnswerPostToCreateUser1, { id: 1 });
            const expected = Object.assign({}, returnedFromService);
            service
                .create(1, metisAnswerPostToCreateUser1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost text field', fakeAsync(() => {
            const returnedFromService = Object.assign({}, metisResolvingAnswerPostUser1, { content: 'This is another test answer' });
            const expected = Object.assign({}, returnedFromService);
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost resolvesPost field', fakeAsync(() => {
            const returnedFromService = Object.assign({}, metisResolvingAnswerPostUser1, { resolvesPost: true });
            const expected = Object.assign({}, returnedFromService);
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a AnswerPost', fakeAsync(() => {
            service.delete(1, metisResolvingAnswerPostUser1).subscribe((resp) => expect(resp.ok).toBeTrue());

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should get source answer posts by IDs', fakeAsync(() => {
            const answerPostIds = [1, 2, 3];
            const returnedFromService = [
                metisResolvingAnswerPostUser1,
                Object.assign({}, metisResolvingAnswerPostUser1, { id: 2 }),
                Object.assign({}, metisResolvingAnswerPostUser1, { id: 3 }),
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
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
