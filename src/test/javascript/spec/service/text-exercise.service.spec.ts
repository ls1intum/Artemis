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
import * as chai from 'chai';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TutorEffort } from 'app/entities/tutor-effort.model';

const expect = chai.expect;

describe('TextExercise Service', () => {
    let injector: TestBed;
    let service: TextExerciseService;
    let httpMock: HttpTestingController;
    let elemDefault: TextExercise;
    let requestResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        requestResult = {} as HttpResponse<TextExercise>;
        injector = getTestBed();
        service = injector.get(TextExerciseService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new TextExercise(new Course(), undefined);
        elemDefault.assessmentDueDate = moment();
        elemDefault.dueDate = moment();
        elemDefault.releaseDate = moment();
        elemDefault.studentParticipations = new Array<StudentParticipation>();
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
    });

    afterEach(() => {
        httpMock.verify();
    });
});
