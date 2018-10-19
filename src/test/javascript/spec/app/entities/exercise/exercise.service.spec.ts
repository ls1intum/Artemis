/* tslint:disable max-line-length */
import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { take, map } from 'rxjs/operators';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { ExerciseService } from 'app/entities/exercise/exercise.service';
import { IExercise, Exercise, DifficultyLevel } from 'app/shared/model/exercise.model';

describe('Service Tests', () => {
    describe('Exercise Service', () => {
        let injector: TestBed;
        let service: ExerciseService;
        let httpMock: HttpTestingController;
        let elemDefault: IExercise;
        let currentDate: moment.Moment;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule]
            });
            injector = getTestBed();
            service = injector.get(ExerciseService);
            httpMock = injector.get(HttpTestingController);
            currentDate = moment();

            elemDefault = new Exercise(0, 'AAAAAAA', 'AAAAAAA', 'AAAAAAA', currentDate, currentDate, 0, DifficultyLevel.EASY, 'AAAAAAA');
        });

        describe('Service methods', async () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign(
                    {
                        releaseDate: currentDate.format(DATE_TIME_FORMAT),
                        dueDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: elemDefault }));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should create a Exercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                        releaseDate: currentDate.format(DATE_TIME_FORMAT),
                        dueDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        releaseDate: currentDate,
                        dueDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .create(new Exercise(null))
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a Exercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        problemStatement: 'BBBBBB',
                        gradingInstructions: 'BBBBBB',
                        title: 'BBBBBB',
                        releaseDate: currentDate.format(DATE_TIME_FORMAT),
                        dueDate: currentDate.format(DATE_TIME_FORMAT),
                        maxScore: 1,
                        difficulty: 'BBBBBB',
                        categories: 'BBBBBB'
                    },
                    elemDefault
                );

                const expected = Object.assign(
                    {
                        releaseDate: currentDate,
                        dueDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should return a list of Exercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        problemStatement: 'BBBBBB',
                        gradingInstructions: 'BBBBBB',
                        title: 'BBBBBB',
                        releaseDate: currentDate.format(DATE_TIME_FORMAT),
                        dueDate: currentDate.format(DATE_TIME_FORMAT),
                        maxScore: 1,
                        difficulty: 'BBBBBB',
                        categories: 'BBBBBB'
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        releaseDate: currentDate,
                        dueDate: currentDate
                    },
                    returnedFromService
                );
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

            it('should delete a Exercise', async () => {
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
