import { TranslateService } from '@ngx-translate/core';
import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { map, take } from 'rxjs/operators';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { expect } from '../entry';

describe('Course Service', () => {
    let injector: TestBed;
    let service: CourseManagementService;
    let httpMock: HttpTestingController;
    let elemDefault: Course;
    let currentDate: moment.Moment;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(CourseManagementService);
        httpMock = injector.get(HttpTestingController);
        currentDate = moment();

        elemDefault = new Course();
        elemDefault.id = 0;
        elemDefault.title = 'AAAAAAA';
        elemDefault.description = 'AAAAAAA';
        elemDefault.shortName = 'AAAAAAA';
        elemDefault.title = 'AAAAAAA';
        elemDefault.startDate = currentDate;
        elemDefault.endDate = currentDate;
        elemDefault.complaintsEnabled = false;
        elemDefault.studentQuestionsEnabled = false;
    });

    describe('Service methods', async () => {
        it('should find an element', async () => {
            const returnedFromService = Object.assign(
                {
                    startDate: currentDate.format(DATE_TIME_FORMAT),
                    endDate: currentDate.format(DATE_TIME_FORMAT),
                },
                elemDefault,
            );
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should create a Course', async () => {
            const returnedFromService = Object.assign(
                {
                    id: 0,
                    startDate: currentDate.format(DATE_TIME_FORMAT),
                    endDate: currentDate.format(DATE_TIME_FORMAT),
                },
                elemDefault,
            );
            const expected = Object.assign(
                {
                    startDate: currentDate,
                    endDate: currentDate,
                },
                returnedFromService,
            );
            service
                .create(new Course())
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a Course', async () => {
            const returnedFromService = Object.assign(
                {
                    title: 'BBBBBB',
                    studentGroupName: 'BBBBBB',
                    teachingAssistantGroupName: 'BBBBBB',
                    instructorGroupName: 'BBBBBB',
                    startDate: currentDate.format(DATE_TIME_FORMAT),
                    endDate: currentDate.format(DATE_TIME_FORMAT),
                    onlineCourse: true,
                },
                elemDefault,
            );

            const expected = Object.assign(
                {
                    startDate: currentDate,
                    endDate: currentDate,
                },
                returnedFromService,
            );
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should return a list of Course', async () => {
            const returnedFromService = Object.assign(
                {
                    title: 'BBBBBB',
                    studentGroupName: 'BBBBBB',
                    teachingAssistantGroupName: 'BBBBBB',
                    instructorGroupName: 'BBBBBB',
                    startDate: currentDate.format(DATE_TIME_FORMAT),
                    endDate: currentDate.format(DATE_TIME_FORMAT),
                    onlineCourse: true,
                },
                elemDefault,
            );
            const expected = Object.assign(
                {
                    startDate: currentDate,
                    endDate: currentDate,
                },
                returnedFromService,
            );
            service
                .findAllForDashboard()
                .pipe(
                    take(1),
                    map((resp) => resp.body),
                )
                .subscribe((body) => expect(body).toContainEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify([returnedFromService]));
            httpMock.verify();
        });

        it('should delete a Course', async () => {
            service.delete(123).subscribe((resp) => expect(resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});
