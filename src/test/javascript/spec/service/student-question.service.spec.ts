import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestion } from 'app/entities/student-question.model';

const expect = chai.expect;

describe('StudentQuestion Service', () => {
    let injector: TestBed;
    let service: StudentQuestionService;
    let httpMock: HttpTestingController;
    let elemDefault: StudentQuestion;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        expectedResult = {} as HttpResponse<StudentQuestion>;
        injector = getTestBed();
        service = injector.get(StudentQuestionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new StudentQuestion();
        elemDefault.id = 0;
        elemDefault.creationDate = undefined;
        elemDefault.questionText = 'This is a test question';
    });

    describe('Service methods', () => {
        it('should create a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(new StudentQuestion())
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should update a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, questionText: 'This is another test question' };

            const expected = { ...returnedFromService };
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should delete a StudentQuestion', async () => {
            service.delete(123).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            expect(expectedResult).to.be.true;
        });

        it('should update the votes of a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, votes: 42 };

            const expected = { ...returnedFromService };
            service
                .updateVotes(expected.id!, 0)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
