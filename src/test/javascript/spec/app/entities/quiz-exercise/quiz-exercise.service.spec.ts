/* tslint:disable max-line-length */
import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { take, map } from 'rxjs/operators';
import { QuizExerciseService } from 'app/entities/quiz-exercise/quiz-exercise.service';
import { IQuizExercise, QuizExercise } from 'app/shared/model/quiz-exercise.model';

describe('Service Tests', () => {
    describe('QuizExercise Service', () => {
        let injector: TestBed;
        let service: QuizExerciseService;
        let httpMock: HttpTestingController;
        let elemDefault: IQuizExercise;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule]
            });
            injector = getTestBed();
            service = injector.get(QuizExerciseService);
            httpMock = injector.get(HttpTestingController);

            elemDefault = new QuizExercise(0, 'AAAAAAA', 'AAAAAAA', false, 0, false, false, false, 0);
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

            it('should create a QuizExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0
                    },
                    elemDefault
                );
                const expected = Object.assign({}, returnedFromService);
                service
                    .create(new QuizExercise(null))
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a QuizExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        description: 'BBBBBB',
                        explanation: 'BBBBBB',
                        randomizeQuestionOrder: true,
                        allowedNumberOfAttempts: 1,
                        isVisibleBeforeStart: true,
                        isOpenForPractice: true,
                        isPlannedToStart: true,
                        duration: 1
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

            it('should return a list of QuizExercise', async () => {
                const returnedFromService = Object.assign(
                    {
                        description: 'BBBBBB',
                        explanation: 'BBBBBB',
                        randomizeQuestionOrder: true,
                        allowedNumberOfAttempts: 1,
                        isVisibleBeforeStart: true,
                        isOpenForPractice: true,
                        isPlannedToStart: true,
                        duration: 1
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

            it('should delete a QuizExercise', async () => {
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
