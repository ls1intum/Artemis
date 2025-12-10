import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { PlagiarismOptions } from 'app/plagiarism/shared/entities/PlagiarismOptions';
import dayjs from 'dayjs/esm';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TutorEffort } from 'app/assessment/shared/entities/tutor-effort.model';
import { PlagiarismResult } from 'app/plagiarism/shared/entities/PlagiarismResult';

describe('TextExercise Service', () => {
    let service: TextExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: TextExercise;
    let requestResult: any;
    let plagiarismResults: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                SessionStorageService,
            ],
        });
        requestResult = {} as HttpResponse<TextExercise>;
        service = TestBed.inject(TextExerciseService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new TextExercise(new Course(), undefined);
        elemDefault.assessmentDueDate = dayjs();
        elemDefault.dueDate = dayjs();
        elemDefault.releaseDate = dayjs();
        elemDefault.studentParticipations = new Array<StudentParticipation>();
        plagiarismResults = new PlagiarismResult();
        plagiarismResults.exercise = elemDefault;
        plagiarismResults.comparisons = [];
        plagiarismResults.duration = 40;
        plagiarismResults.similarityDistribution = [4, 10];
    });

    describe('Service methods', () => {
        it('should find an element', () => {
            const returnedFromService = Object.assign({}, elemDefault);
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            expect(requestResult.body).toEqual(elemDefault);
        });

        it('should create a TextExercise', () => {
            const returnedFromService = Object.assign({ id: 0 }, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .create(new TextExercise(undefined, undefined))
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            expect(requestResult.body).toEqual(expected);
        });

        it('should update a TextExercise', () => {
            const returnedFromService = Object.assign({ exampleSolution: 'BBBBBB' }, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(requestResult.body).toEqual(expected);
        });

        it('should return a list of TextExercise', () => {
            const returnedFromService = Object.assign({ exampleSolution: 'BBBBBB' }, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .query(expected)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush([returnedFromService]);
            expect(requestResult.body).toEqual([expected]);
        });

        it('should delete a TextExercise', () => {
            service.delete(123).subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            expect(requestResult.status).toBe(200);
        });

        it('should calculate and return tutor efforts', () => {
            const exerciseId = 1;
            const courseId = 1;
            service.calculateTutorEffort(exerciseId, courseId).subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            const returnedFromService: TutorEffort[] = [
                {
                    courseId,
                    exerciseId,
                    numberOfSubmissionsAssessed: 1,
                    totalTimeSpentMinutes: 1,
                },
            ];
            req.flush(returnedFromService);
            expect(requestResult).toEqual(returnedFromService);
        });

        it('should import a text exercise', () => {
            const textExerciseReturned = Object.assign({}, elemDefault);
            textExerciseReturned.id = 123;
            service
                .import(textExerciseReturned)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).toEqual(textExerciseReturned);
                });
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(textExerciseReturned);
        });

        it('should re-evaluate and update a text exercise', () => {
            const textExerciseReturned = Object.assign({}, elemDefault);
            textExerciseReturned.id = 123;
            service
                .reevaluateAndUpdate(textExerciseReturned)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).toEqual(textExerciseReturned);
                });
            const request = httpMock.expectOne({ method: 'PUT' });
            request.flush(textExerciseReturned);
        });

        it('should check plagiarism', () => {
            const options = new PlagiarismOptions(5, 3, 3);
            const expectedReturn = Object.assign({}, plagiarismResults);
            service
                .checkPlagiarism(123, options)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(expectedReturn);
            expect(requestResult).toEqual(expectedReturn);
        });

        it('should get plagiarism result', () => {
            const expectedReturnValue = Object.assign({}, plagiarismResults);
            service
                .getLatestPlagiarismResult(123)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(expectedReturnValue);
            expect(requestResult).toEqual(expectedReturnValue);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
