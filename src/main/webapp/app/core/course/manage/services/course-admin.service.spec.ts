import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { CourseAdminService } from 'app/core/course/manage/services/course-admin.service';
import { UMLDiagramType } from '@ls1intum/apollon';
import { provideHttpClient } from '@angular/common/http';

describe('Course Admin Service', () => {
    let courseAdminService: CourseAdminService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/core/admin/courses';
    let course: Course;
    let exercises: Exercise[];
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

        course = new Course();
        course.id = 1234;
        course.title = 'testTitle';
        exercises = [new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined), new ModelingExercise(UMLDiagramType.ComponentDiagram, undefined, undefined)];
        course.exercises = exercises;
        course.lectures = undefined;
        course.startDate = undefined;
        course.endDate = undefined;
        course.competencies = [];
        course.prerequisites = [];
        returnedFromService = Object.assign({}, course) as Course;
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should create course', fakeAsync(() => {
        delete course.id;

        courseAdminService
            .create(Object.assign({}, course))
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual(Object.assign({}, course, { id: 1234 })));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should delete a course', fakeAsync(() => {
        courseAdminService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({});
        tick();
    }));
});
