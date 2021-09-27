import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { metisAnswerPostToCreateUser1, metisAnswerPostUser1 } from '../../helpers/sample/metis-sample-data';

const expect = chai.expect;

describe('AnswerPost Service', () => {
    let injector: TestBed;
    let service: AnswerPostService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(AnswerPostService);
        httpMock = injector.get(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a AnswerPost', fakeAsync(() => {
            const returnedFromService = { ...metisAnswerPostToCreateUser1, id: 1 };
            const expected = { ...returnedFromService };
            service
                .create(1, metisAnswerPostToCreateUser1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost text field', fakeAsync(() => {
            const returnedFromService = { ...metisAnswerPostUser1, content: 'This is another test answer' };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost tutorApproved field', fakeAsync(() => {
            const returnedFromService = { ...metisAnswerPostUser1, tutorApproved: true };
            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a AnswerPost', fakeAsync(() => {
            service.delete(1, metisAnswerPostUser1).subscribe((resp) => expect(resp.ok).to.be.true);

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
