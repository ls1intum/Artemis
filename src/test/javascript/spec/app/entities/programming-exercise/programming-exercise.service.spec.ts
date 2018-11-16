/* tslint:disable max-line-length */
import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { take, map } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';
import { IProgrammingExercise, ProgrammingExercise } from 'app/shared/model/programming-exercise.model';

describe('Service Tests', () => {
    describe('ProgrammingExercise Service', () => {
        let injector: TestBed;
        let service: ProgrammingExerciseService;
        let httpMock: HttpTestingController;
        let elemDefault: IProgrammingExercise;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule]
            });
            injector = getTestBed();
            service = injector.get(ProgrammingExerciseService);
            httpMock = injector.get(HttpTestingController);

            elemDefault = new ProgrammingExercise(0, 'AAAAAAA', 'AAAAAAA', 'AAAAAAA', false, false);
        });

        describe('Service methods', async () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign({}, elemDefault);
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: elemDefault }));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should create a ProgrammingExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0
                    },
                    elemDefault
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .create(new ProgrammingExercise(null))
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a ProgrammingExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        baseRepositoryUrl: 'BBBBBB',
                        solutionRepositoryUrl: 'BBBBBB',
                        baseBuildPlanId: 'BBBBBB',
                        publishBuildPlanUrl: true,
                        allowOnlineEditor: true
                    },
                    elemDefault
                );

                const expected = Object.assign({}, returnedFromService);
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should return a list of ProgrammingExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        baseRepositoryUrl: 'BBBBBB',
                        solutionRepositoryUrl: 'BBBBBB',
                        baseBuildPlanId: 'BBBBBB',
                        publishBuildPlanUrl: true,
                        allowOnlineEditor: true
                    },
                    elemDefault
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .query(expected)
                    .pipe(
                        take(1),
                        map(resp => resp.body)
                    )
                    .subscribe(body => expect(body).toContainEqual(expected));
                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify([returnedFromService]));
                httpMock.verify();
            });

            it('should delete a ProgrammingExercise', async () => {
                const rxPromise = service.delete(123).subscribe(resp => expect(resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});
