import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Subject, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Event, NavigationEnd, Router, convertToParamMap } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamManagementComponent } from 'app/exam/manage/exam-management/exam-management.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { DurationPipe } from 'app/foundation/pipes/artemis-duration.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlertService } from 'app/foundation/service/alert.service';

describe('Exam Management Component', () => {
    const course = { id: 456 } as Course;
    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    let comp: ExamManagementComponent;
    let fixture: ComponentFixture<ExamManagementComponent>;
    let service: ExamManagementService;
    let courseManagementService: CourseManagementService;
    let eventManager: EventManager;
    let alertService: AlertService;
    let routerEventsSubject: Subject<Event>;

    let mockRoute: any;
    let mockRouter: any;

    beforeEach(async () => {
        routerEventsSubject = new Subject<Event>();
        mockRouter = {
            events: routerEventsSubject.asObservable(),
            url: '/course-management/456/exams',
            navigate: vi.fn(),
        };
        mockRoute = {
            snapshot: {
                paramMap: convertToParamMap({ courseId: course.id }),
                firstChild: undefined,
            },
        };

        await TestBed.configureTestingModule({
            imports: [
                ExamManagementComponent,
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockPipe(DurationPipe),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                SessionStorageService,
                LocalStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: Router, useValue: mockRouter },
                { provide: ActivatedRoute, useValue: mockRoute },
                EventManager,
                AlertService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamManagementComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(ExamManagementService);
        courseManagementService = TestBed.inject(CourseManagementService);
        eventManager = TestBed.inject(EventManager);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        vi.restoreAllMocks();
    });

    it('should call find of courseManagementService to get course on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(courseManagementService.find).toHaveBeenCalledOnce();
        expect(comp.course()).toEqual(course);
    });

    it('should handle error when courseManagementService.find fails', () => {
        const errorResponse = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        vi.spyOn(courseManagementService, 'find').mockReturnValue(throwError(() => errorResponse));
        const alertSpy = vi.spyOn(alertService, 'error');

        comp.ngOnInit();

        expect(alertSpy).toHaveBeenCalled();
    });

    it('should call loadAllExamsForCourse on init', () => {
        // GIVEN
        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeExams));

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findAllExamsForCourse).toHaveBeenCalledOnce();
        expect(comp.exams()).toEqual([exam]);
    });

    it('should handle error when findAllExamsForCourse fails', () => {
        comp.course.set(course);
        const errorResponse = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(throwError(() => errorResponse));
        const alertSpy = vi.spyOn(alertService, 'error');

        comp.loadAllExamsForCourse();

        expect(alertSpy).toHaveBeenCalled();
    });

    it('should call findAllExamsForCourse on examListModification event being fired after registering for exam changes', () => {
        // GIVEN
        comp.course.set(course);
        const responseFakeExams = { body: [exam] } as HttpResponse<Exam[]>;
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeExams));

        // WHEN
        comp.registerChangeInExams();
        eventManager.broadcast({ name: 'examListModification', content: 'dummy' });

        // THEN
        expect(service.findAllExamsForCourse).toHaveBeenCalledOnce();
        expect(comp.exams()).toEqual([exam]);
    });

    it('should toggle sidebar collapsed state', () => {
        expect(comp.isCollapsed()).toBe(false);
        comp.toggleSidebar();
        expect(comp.isCollapsed()).toBe(true);
        comp.toggleSidebar();
        expect(comp.isCollapsed()).toBe(false);
    });

    it('should update currentExam when route has child with examId', () => {
        comp.exams.set([exam]);
        mockRoute.snapshot.firstChild = {
            firstChild: undefined,
            paramMap: convertToParamMap({ examId: exam.id }),
        };

        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of({ body: [exam] } as HttpResponse<Exam[]>));

        comp.ngOnInit();

        expect(comp.currentExam()).toEqual(exam);
    });

    it('should not set currentExam on direct exam import route', () => {
        comp.exams.set([exam]);
        mockRoute.snapshot.firstChild = {
            firstChild: undefined,
            paramMap: convertToParamMap({ examId: exam.id }),
            routeConfig: { path: 'import/:examId' },
        };

        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of({ body: [exam] } as HttpResponse<Exam[]>));

        comp.ngOnInit();

        expect(comp.currentExam()).toBeUndefined();
    });

    it('should set currentExam on exercise import route within an exam', () => {
        comp.exams.set([exam]);
        mockRoute.snapshot.firstChild = {
            firstChild: undefined,
            paramMap: convertToParamMap({ examId: exam.id, exerciseGroupId: 789, exerciseId: 101 }),
            routeConfig: { path: ':examId/exercise-groups/:exerciseGroupId/modeling-exercises/import/:exerciseId' },
        };

        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of({ body: [exam] } as HttpResponse<Exam[]>));

        comp.ngOnInit();

        expect(comp.currentExam()).toEqual(exam);
    });

    it('should update currentExam on NavigationEnd event', () => {
        comp.exams.set([exam]);
        mockRoute.snapshot.firstChild = {
            firstChild: undefined,
            paramMap: convertToParamMap({ examId: exam.id }),
        };

        const responseFakeCourse = { body: course as Course } as HttpResponse<Course>;
        vi.spyOn(courseManagementService, 'find').mockReturnValue(of(responseFakeCourse));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of({ body: [exam] } as HttpResponse<Exam[]>));

        comp.ngOnInit();
        expect(comp.currentExam()).toEqual(exam);

        // Simulate navigating to page without examId
        mockRoute.snapshot.firstChild = undefined;
        routerEventsSubject.next(new NavigationEnd(1, '/course-management/456/exams', '/course-management/456/exams'));

        expect(comp.currentExam()).toBeUndefined();
    });
});
