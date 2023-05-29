import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

describe('Lecture Service', () => {
    let httpMock: HttpTestingController;
    let service: LectureService;
    const resourceUrl = 'api/lectures';
    let expectedResult: any;
    let elemDefault: Lecture;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(LectureService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<Lecture>;
        elemDefault = new Lecture();
        elemDefault.startDate = dayjs();
        elemDefault.course = new Course();
        elemDefault.description = 'new service test Lecture';
        elemDefault.endDate = dayjs();
        elemDefault.id = 1;
        elemDefault.isAtLeastEditor = false;
        elemDefault.isAtLeastInstructor = false;
        elemDefault.channelName = 'lecture-default';
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should create a lecture in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'POST',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should update a lecture in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .update(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'PUT',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find a lecture with details in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService, posts: [] };
            const lectureId = elemDefault.id!;
            service
                .findWithDetails(lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${lectureId}/details`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find a lecture with details and with slides in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService, posts: [] };
            const lectureId = elemDefault.id!;
            service
                .findWithDetailsWithSlides(lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${lectureId}/details-with-slides`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find a lecture in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            const lectureId = elemDefault.id!;
            service
                .find(lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${lectureId}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should invoke query', async () => {
            const returnedFromService = [elemDefault];
            const expected = returnedFromService;
            service
                .query({})
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should get all lectures by courseId', async () => {
            const returnedFromService = [elemDefault];
            const expected = returnedFromService;
            const courseId = 1;
            service
                .findAllByCourseId(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/lectures?withLectureUnits=0`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should import lecture', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            const lectureId = elemDefault.id!;
            const courseId = 1;
            service
                .import(courseId, lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/lectures/import/${lectureId}?courseId=${courseId}`,
                method: 'POST',
            });

            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should get all lectures with lecture units slides by courseId', async () => {
            const returnedFromService = [elemDefault];
            const expected = returnedFromService;
            const courseId = 1;
            service
                .findAllByCourseIdWithSlides(courseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/courses/${courseId}/lectures-with-slides`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete a lecture in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const lectureId = elemDefault.id!;
            service
                .delete(lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${lectureId}`,
                method: 'DELETE',
            });
            req.flush(returnedFromService);
            expect(req.request.method).toBe('DELETE');
        });

        it('should convert Dates from server', async () => {
            const results = service.convertLectureArrayDatesFromServer([elemDefault, elemDefault]);
            expect(results).toEqual([elemDefault, elemDefault]);
        });
    });
});
