import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { Course } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { LectureForOverview } from 'app/lecture/shared/entities/lecture-for-overview.model';
import { AccountService } from 'app/core/auth/account.service';

describe('Lecture Service', () => {
    let httpMock: HttpTestingController;
    let service: LectureService;
    const resourceUrl = '/api/lecture/lectures';
    let elemDefault: Lecture;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(LectureService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new Lecture();
        elemDefault.startDate = dayjs();
        elemDefault.course = new Course();
        elemDefault.course.id = 42;
        elemDefault.description = 'new service test Lecture';
        elemDefault.endDate = dayjs();
        elemDefault.id = 1;
        elemDefault.title = 'Test Lecture';
        elemDefault.isAtLeastEditor = false;
        elemDefault.isAtLeastInstructor = false;
        elemDefault.channelName = 'lecture-default';
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        const serverLecture = { id: 1, title: 'Test Lecture', isTutorialLecture: false, startDate: '2026-10-01T08:00:00Z', endDate: '2026-10-01T09:30:00Z' };

        const expectSimpleLectureBody = (body: Record<string, unknown>) => {
            expect(body['title']).toBe('Test Lecture');
            expect(body['description']).toBe('new service test Lecture');
            expect(body['channelName']).toBe('lecture-default');
            expect(body['isTutorialLecture']).toBe(false);
            expect(body['course']).toEqual({ id: 42 });
            expect(typeof body['startDate']).toBe('string');
            expect(typeof body['endDate']).toBe('string');
            expect(body['attachments']).toBeUndefined();
            expect(body['lectureUnits']).toBeUndefined();
            expect(body['posts']).toBeUndefined();
            expect(body['isAtLeastEditor']).toBeUndefined();
            expect(body['isAtLeastInstructor']).toBeUndefined();
        };

        it('should create a lecture from the simple lecture request', () => {
            let result: Lecture | undefined;
            service.create(elemDefault).subscribe((lecture) => (result = lecture));
            const req = httpMock.expectOne({ url: resourceUrl, method: 'POST' });
            expectSimpleLectureBody(req.request.body);
            req.flush(serverLecture);

            expect(result).toBeInstanceOf(Lecture);
            expect(result?.id).toBe(1);
            expect(dayjs.isDayjs(result?.startDate)).toBe(true);
        });

        it('should update a lecture from the simple lecture request', () => {
            let result: Lecture | undefined;
            service.update(elemDefault).subscribe((lecture) => (result = lecture));
            const req = httpMock.expectOne({ url: resourceUrl, method: 'PUT' });
            expect(req.request.body['id']).toBe(1);
            expectSimpleLectureBody(req.request.body);
            req.flush(serverLecture);

            expect(result?.id).toBe(1);
            expect(result?.title).toBe('Test Lecture');
        });

        it('should publish the saved title for the breadcrumb and the page title', () => {
            const titleSpy = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');

            service.update(elemDefault).subscribe();
            httpMock.expectOne({ url: resourceUrl, method: 'PUT' }).flush({ ...serverLecture, title: 'Renamed Lecture' });

            expect(titleSpy).toHaveBeenCalledWith(EntityType.LECTURE, [elemDefault.id], 'Renamed Lecture');
        });

        it('should find a lecture, convert its units and set access rights and the title', () => {
            const accessSpy = vi.spyOn(TestBed.inject(AccountService), 'setAccessRightsForCourse').mockImplementation((course) => {
                course.isAtLeastEditor = true;
                course.isAtLeastInstructor = false;
            });
            const titleSpy = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
            let result: Lecture | undefined;

            service.find(3).subscribe((lecture) => (result = lecture));
            httpMock
                .expectOne({ url: `${resourceUrl}/3`, method: 'GET' })
                .flush({ id: 3, title: 'Found', course: { id: 42 }, lectureUnits: [{ id: 5, type: 'text', releaseDate: '2026-10-02T08:00:00Z' }] });

            expect(dayjs.isDayjs(result?.lectureUnits?.[0].releaseDate)).toBe(true);
            expect(accessSpy).toHaveBeenCalledOnce();
            expect(result?.isAtLeastEditor).toBe(true);
            expect(result?.isAtLeastInstructor).toBe(false);
            expect(titleSpy).toHaveBeenCalledWith(EntityType.LECTURE, [3], 'Found');
        });

        it('should find a lecture with details and default absent posts to an empty list', () => {
            let result: Lecture | undefined;
            service.findWithDetails(3).subscribe((lecture) => (result = lecture));
            httpMock.expectOne({ url: `${resourceUrl}/3/details`, method: 'GET' }).flush({ id: 3, title: 'Detailed' });

            expect(result?.id).toBe(3);
            expect(result?.posts).toEqual([]);
        });

        it('should keep the posts of a lecture with details', () => {
            let result: Lecture | undefined;
            service.findWithDetails(3).subscribe((lecture) => (result = lecture));
            httpMock.expectOne({ url: `${resourceUrl}/3/details`, method: 'GET' }).flush({ id: 3, posts: [{ id: 9 }] });

            expect(result?.posts).toEqual([{ id: 9 }]);
        });

        it.each([
            { name: 'findAllByCourseId', call: () => service.findAllByCourseId(1), url: '/api/lecture/courses/1/lectures' },
            { name: 'findAllTutorialLecturesByCourseId', call: () => service.findAllTutorialLecturesByCourseId(1), url: '/api/lecture/courses/1/tutorial-lectures' },
            { name: 'findAllByCourseIdWithSlides', call: () => service.findAllByCourseIdWithSlides(1), url: '/api/lecture/courses/1/lectures-with-slides' },
        ])('$name should convert the lectures, set access rights and register titles', ({ call, url }) => {
            const accessSpy = vi.spyOn(TestBed.inject(AccountService), 'setAccessRightsForCourse').mockImplementation(() => {});
            const titleSpy = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
            let result: Lecture[] | undefined;

            call().subscribe((lectures) => (result = lectures));
            httpMock.expectOne({ url, method: 'GET' }).flush([{ id: 7, title: 'A', course: { id: 1 }, startDate: '2026-10-01T08:00:00Z' }]);

            expect(result).toHaveLength(1);
            expect(result?.[0]).toBeInstanceOf(Lecture);
            expect(dayjs.isDayjs(result?.[0].startDate)).toBe(true);
            expect(accessSpy).toHaveBeenCalledOnce();
            expect(titleSpy).toHaveBeenCalledWith(EntityType.LECTURE, [7], 'A');
        });

        it('should load the narrow lecture overview and register converted lecture titles', () => {
            const startDate = '2026-08-01T10:00:00.000Z';
            const endDate = '2026-08-01T12:00:00.000Z';
            const overviewLecture = { id: 7, title: 'Overview lecture', startDate, endDate, isTutorialLecture: false };
            const titleSpy = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
            let received: LectureForOverview[] | undefined;

            service.findAllByCourseIdForOverview(42).subscribe((lectures) => (received = lectures));
            const request = httpMock.expectOne({ method: 'GET', url: '/api/lecture/courses/42/lectures-for-overview' });
            expect(request.request.params.keys()).toEqual([]);
            request.flush([overviewLecture]);

            expect(received).toHaveLength(1);
            expect(received?.[0]).toMatchObject({ id: 7, title: 'Overview lecture', isTutorialLecture: false });
            expect(dayjs.isDayjs(received?.[0].startDate)).toBe(true);
            expect(dayjs.isDayjs(received?.[0].endDate)).toBe(true);
            expect(received?.[0].startDate?.toISOString()).toBe(startDate);
            expect(received?.[0].endDate?.toISOString()).toBe(endDate);
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(EntityType.LECTURE, [7], 'Overview lecture');
        });

        it('should import a lecture into a course by source lecture id', () => {
            let result: Lecture | undefined;
            service.import(1, 5).subscribe((lecture) => (result = lecture));
            const req = httpMock.expectOne((request) => request.url.startsWith(`${resourceUrl}/import`));

            expect(req.request.method).toBe('POST');
            expect(req.request.url).toBe(`${resourceUrl}/import?sourceLectureId=5&courseId=1`);
            expect(req.request.body).toBeNull();
            req.flush(serverLecture);
            expect(result?.id).toBe(1);
        });

        it('should propagate a failing request', () => {
            const error = vi.fn();
            service.find(3).subscribe({ error });
            httpMock.expectOne({ url: `${resourceUrl}/3`, method: 'GET' }).flush({}, { status: 404, statusText: 'Not Found' });

            expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 404 }));
        });
    });
});
