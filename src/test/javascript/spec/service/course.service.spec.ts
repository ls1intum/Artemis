import { TranslateService } from '@ngx-translate/core';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { map, take } from 'rxjs/operators';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockRouter } from '../helpers/mocks/mock-router';

describe('Course Service', () => {
    let service: CourseManagementService;
    let httpMock: HttpTestingController;
    let elemDefault: Course;
    let currentDate: dayjs.Dayjs;
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
        service = TestBed.inject(CourseManagementService);
        httpMock = TestBed.inject(HttpTestingController);
        currentDate = dayjs();

        elemDefault = new Course();
        elemDefault.id = 0;
        elemDefault.title = 'AAAAAAA';
        elemDefault.description = 'AAAAAAA';
        elemDefault.shortName = 'AAAAAAA';
        elemDefault.title = 'AAAAAAA';
        elemDefault.startDate = currentDate;
        elemDefault.endDate = currentDate;
        elemDefault.semester = 'SS20';
        elemDefault.complaintsEnabled = false;
        elemDefault.postsEnabled = false;
    });

    it('should find an element', fakeAsync(() => {
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
        req.flush(returnedFromService);
        tick();
    }));

    it('should create a Course', fakeAsync(() => {
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
        req.flush(returnedFromService);
        tick();
    }));

    it('should update a Course', fakeAsync(() => {
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
        req.flush(returnedFromService);
        tick();
    }));

    it('should return a list of Course', fakeAsync(() => {
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
                exercises: [],
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
        req.flush([returnedFromService]);
        tick();
    }));

    it('should delete a Course', fakeAsync(() => {
        service.delete(123).subscribe((resp) => expect(resp.ok).toBeTrue());

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
