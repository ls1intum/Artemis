import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';

describe('Course Admin Service', () => {
    let courseAdminService: CourseAdminService;
    let httpMock: HttpTestingController;
    const resourceUrl = SERVER_API_URL + 'api/admin/courses';
    let course: Course;
    let exercises: Exercise[];
    let returnedFromService: any;

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
        course.learningGoals = [];
        course.prerequisites = [];
        returnedFromService = { ...course } as Course;
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should create course', fakeAsync(() => {
        courseAdminService
            .create({ ...course })
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({ ...course, id: 1234 }));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        req.flush(returnedFromService);
        tick();
    }));

    it('should delete a course', fakeAsync(() => {
        courseAdminService
            .delete(course.id!)
            .pipe(take(1))
            .subscribe((res) => expect(res.ok).toBeTrue());
        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/${course.id}` });
        req.flush({ status: 200 });
        tick();
    }));
});
