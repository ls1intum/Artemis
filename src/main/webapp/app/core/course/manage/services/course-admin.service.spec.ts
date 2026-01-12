import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { createSampleCourse } from 'test/helpers/sample/course-sample-data';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { provideHttpClient } from '@angular/common/http';

describe('Course Admin Service', () => {
    setupTestBed({ zoneless: true });

    let courseAdminService: CourseAdminService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/core/admin/courses';
    let course: Course;
    let returnedFromService: any;

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
        returnedFromService = { ...course } as Course;
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should create course', () => {
        delete course.id;

        courseAdminService
            .create({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({ ...course, id: 1234 }));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
    });

    it('should delete a course', () => {
        courseAdminService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
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
