import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { metisAnswerPostToCreateUser1, metisResolvingAnswerPostUser1 } from '../../helpers/sample/metis-sample-data';

describe('AnswerPost Service', () => {
    let service: AnswerPostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(AnswerPostService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a AnswerPost', fakeAsync(() => {
            const returnedFromService = { ...metisAnswerPostToCreateUser1, id: 1 };
            const expected = { ...returnedFromService };
            service
                .create(1, metisAnswerPostToCreateUser1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost text field', fakeAsync(() => {
            const returnedFromService = { ...metisResolvingAnswerPostUser1, content: 'This is another test answer' };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost resolvesPost field', fakeAsync(() => {
            const returnedFromService = { ...metisResolvingAnswerPostUser1, resolvesPost: true };
            const expected = { ...returnedFromService };
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
    });

    afterEach(() => {
        httpMock.verify();
    });
});
