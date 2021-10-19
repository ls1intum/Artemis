import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Router } from '@angular/router';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { HttpResponse } from '@angular/common/http';
import { TextExerciseClusterStatistics } from 'app/entities/text-exercise-cluster-statistics.model';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';
import * as chai from 'chai';
import dayjs from 'dayjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TutorEffort } from 'app/entities/tutor-effort.model';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';

const expect = chai.expect;

describe('TextExercise Service', () => {
    let injector: TestBed;
    let service: TextExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: TextExercise;
    let requestResult: any;
    let plagiarismResults: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        });
        requestResult = {} as HttpResponse<TextExercise>;
        injector = getTestBed();
        service = injector.get(TextExerciseService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new TextExercise(new Course(), undefined);
        elemDefault.assessmentDueDate = dayjs();
        elemDefault.dueDate = dayjs();
        elemDefault.releaseDate = dayjs();
        elemDefault.studentParticipations = new Array<StudentParticipation>();
        plagiarismResults = new TextPlagiarismResult();
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
            expect(requestResult.body).to.deep.eq(elemDefault);
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
            expect(requestResult.body).to.deep.eq(expected);
        });

        it('should update a TextExercise', () => {
            const returnedFromService = Object.assign({ sampleSolution: 'BBBBBB' }, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(requestResult.body).to.deep.eq(expected);
        });

        it('should return a list of TextExercise', () => {
            const returnedFromService = Object.assign({ sampleSolution: 'BBBBBB' }, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .query(expected)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush([returnedFromService]);
            expect(requestResult.body).to.deep.equal([expected]);
        });

        it('should delete a TextExercise', () => {
            service.delete(123).subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            expect(requestResult.status).to.equal(200);
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
            expect(requestResult).to.equal(returnedFromService);
        });

        it('should import a text exercise', () => {
            const textExerciseReturned = { ...elemDefault };
            textExerciseReturned.id = 123;
            service
                .import(textExerciseReturned)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).equal(textExerciseReturned);
                });
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(textExerciseReturned);
        });

        it('should re-evaluate and update a text exercise', () => {
            const textExerciseReturned = { ...elemDefault };
            textExerciseReturned.id = 123;
            service
                .reevaluateAndUpdate(textExerciseReturned)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).equal(textExerciseReturned);
                });
            const request = httpMock.expectOne({ method: 'PUT' });
            request.flush(textExerciseReturned);
        });

        it('should check plagiarism', () => {
            const options = new PlagiarismOptions(5, 3, 3);
            const expectedReturn = { ...plagiarismResults };
            service
                .checkPlagiarism(123, options)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(expectedReturn);
            expect(requestResult).to.equal(expectedReturn);
        });

        it('should get plagiarism result', () => {
            const expectedReturnValue = { ...plagiarismResults };
            service
                .getLatestPlagiarismResult(123)
                .pipe(take(1))
                .subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(expectedReturnValue);
            expect(requestResult).to.equal(expectedReturnValue);
        });

        it('should retrieve TextExercise cluster statistics', () => {
            service.getClusterStats(1).subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            const returnedFromService: TextExerciseClusterStatistics[] = [
                {
                    clusterId: 1,
                    clusterSize: 1,
                    numberOfAutomaticFeedbacks: 3,
                    disabled: true,
                },
            ];
            req.flush(returnedFromService);
            expect(requestResult).to.equal(returnedFromService);
        });

        it('should set TextExercise cluster disabled predicate', () => {
            service.setClusterDisabledPredicate(1, 1, true).subscribe((resp) => (requestResult = resp));
            const req = httpMock.expectOne({ method: 'PATCH' });
            req.flush({});
            expect(requestResult).to.deep.equal({});
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
