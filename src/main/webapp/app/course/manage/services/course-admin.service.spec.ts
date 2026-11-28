import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { createSampleCourse } from 'test/helpers/sample/course-sample-data';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseAdminService } from 'app/course/manage/services/course-admin.service';
import { provideHttpClient } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';

describe('Course Admin Service', () => {
    let courseAdminService: CourseAdminService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/admin/courses';
    let course: Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useClass: MockRouter },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        courseAdminService = TestBed.inject(CourseAdminService);
        httpMock = TestBed.inject(HttpTestingController);

        ({ course } = createSampleCourse());
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should create a course and hand back the id the server assigned', () => {
        delete course.id;

        courseAdminService
            .create(course)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({ id: 1234 }));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        expect(req.request.body).toBeInstanceOf(FormData);
        req.flush({ id: 1234 });
    });

    it('should delete a course', () => {
        courseAdminService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
    });

    it('should accept the deletion of a course with 202 and no body', () => {
        let status: number | undefined;
        courseAdminService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => (status = res.status));
        httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` }).flush(null, { status: 202, statusText: 'Accepted' });
        expect(status).toBe(202);
    });

    it('should read the operation progress of a course without an error alert', () => {
        let progress: { startedAt?: unknown } | null | undefined;
        courseAdminService
            .getOperationProgress(course.id!)
            .pipe(take(1))
            .subscribe((res) => (progress = res.body));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/operation-progress` });
        expect(req.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        req.flush({ operationType: 'DELETE', status: 'IN_PROGRESS', startedAt: '2026-11-08T10:00:00Z' });
        expect(progress?.startedAt).toBeDefined();
    });

    it('should answer an empty body when no operation is known', () => {
        let body: unknown = 'unset';
        courseAdminService
            .getOperationProgress(course.id!)
            .pipe(take(1))
            .subscribe((res) => (body = res.body));
        httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/operation-progress` }).flush(null, { status: 204, statusText: 'No Content' });
        expect(body).toBeNull();
    });

    it('should get course summary', () => {
        const expectedSummary = {
            numberOfStudents: 10,
            numberOfTutors: 2,
            numberOfEditors: 1,
            numberOfInstructors: 1,
            numberOfParticipations: 50,
            numberOfSubmissions: 200,
            numberOfConversations: 5,
            numberOfPosts: 100,
            numberOfAnswerPosts: 50,
            numberOfCompetencies: 10,
            numberOfCompetencyProgress: 30,
            numberOfLearnerProfiles: 10,
            numberOfIrisChatSessions: 5,
            numberOfLLMTraces: 25,
            numberOfBuilds: 200,
            numberOfExams: 2,
            numberOfExercises: 5,
            numberOfProgrammingExercises: 2,
            numberOfTextExercises: 1,
            numberOfModelingExercises: 1,
            numberOfQuizExercises: 1,
            numberOfFileUploadExercises: 0,
            numberOfLectures: 3,
            numberOfFaqs: 5,
            numberOfTutorialGroups: 2,
        };
        courseAdminService
            .getCourseSummary(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(expectedSummary));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/${course.id}/summary` });
        req.flush(expectedSummary);
    });

    it('should reset a course', () => {
        courseAdminService
            .reset(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/${course.id}/reset` });
        req.flush({});
    });
});
