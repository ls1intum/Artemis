/* tslint:disable max-line-length */
import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ExerciseHintService } from 'app/entities/exercise-hint/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

const expect = chai.expect;

describe('Service Tests', () => {
    describe('ExerciseHint Service', () => {
        let injector: TestBed;
        let service: ExerciseHintService;
        let httpMock: HttpTestingController;
        let elemDefault: ExerciseHint;
        let expectedResult: any;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule],
            });
            expectedResult = {} as HttpResponse<ExerciseHint>;
            injector = getTestBed();
            service = injector.get(ExerciseHintService);
            httpMock = injector.get(HttpTestingController);

            elemDefault = new ExerciseHint();
            elemDefault.id = 0;
            elemDefault.title = 'AAAAAAA';
            elemDefault.content = 'AAAAAAA';
        });

        describe('Service methods', () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe(resp => (expectedResult = resp));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(returnedFromService);
                expect(expectedResult.body).to.deep.equal(elemDefault);
            });

            it('should create a ExerciseHint', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                    },
                    elemDefault,
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .create(new ExerciseHint())
                    .pipe(take(1))
                    .subscribe(resp => (expectedResult = resp));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(returnedFromService);
                expect(expectedResult.body).to.deep.equal(expected);
            });

            it('should update a ExerciseHint', async () => {
                const returnedFromService = Object.assign(
                    {
                        title: 'BBBBBB',
                        content: 'BBBBBB',
                    },
                    elemDefault,
                );

                const expected = Object.assign({}, returnedFromService);
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe(resp => (expectedResult = resp));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(returnedFromService);
                expect(expectedResult.body).to.deep.equal(expected);
            });

            it('should delete a ExerciseHint', async () => {
                const rxPromise = service.delete(123).subscribe(resp => (expectedResult = resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
                expect(expectedResult).to.be.true;
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
