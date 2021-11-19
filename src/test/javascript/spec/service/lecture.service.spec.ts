import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/lecture.service';
import { Lecture } from 'app/entities/lecture.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs';

const expect = chai.expect;

describe('Lecture Service', () => {
    let injector: TestBed;
    let httpMock: HttpTestingController;
    let service: LectureService;
    const resourceUrl = SERVER_API_URL + 'api/lectures';
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
        injector = getTestBed();
        service = injector.get(LectureService);
        httpMock = injector.get(HttpTestingController);

        expectedResult = {} as HttpResponse<Lecture>;
        elemDefault = new Lecture();
        elemDefault.startDate = dayjs();
        elemDefault.course = new Course();
        elemDefault.description = 'new service test Lecture';
        elemDefault.endDate = dayjs();
        elemDefault.id = 1;
        elemDefault.isAtLeastEditor = false;
        elemDefault.isAtLeastInstructor = false;
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(expectedResult.body).to.deep.equal(expected);
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
            expect(req.request.method).to.equal('DELETE');
        });

        it('should convert Dates from server', async () => {
            const results = service.convertDatesForLecturesFromServer([elemDefault, elemDefault]);
            expect(results).to.deep.equal([elemDefault, elemDefault]);
        });
    });
});
