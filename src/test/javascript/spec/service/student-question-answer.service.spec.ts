import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { StudentQuestionAnswerService } from 'app/overview/student-questions/student-question-answer/student-question-answer.service';
import { StudentQuestionAnswer } from 'app/entities/student-question-answer.model';

const expect = chai.expect;

describe('AnswerPost Service', () => {
    let injector: TestBed;
    let service: StudentQuestionAnswerService;
    let httpMock: HttpTestingController;
    let elemDefault: StudentQuestionAnswer;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(StudentQuestionAnswerService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new StudentQuestionAnswer();
        elemDefault.id = 0;
        elemDefault.answerDate = undefined;
        elemDefault.answerText = 'This is a test answer';
    });

    describe('Service methods', () => {
        it('should create a AnswerPost', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(1, new StudentQuestionAnswer())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a AnswerPost text field', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, answerText: 'This is another test answer' };
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
            const returnedFromService = { ...elemDefault, tutorApproved: true };
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
            service.delete(1, 123).subscribe((resp) => expect(resp.ok).to.be.true);

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
