import { Location } from '@angular/common';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Data, Router, RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check/checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CourseExamArchiveButtonComponent } from 'app/shared/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { DetailOverviewListComponent } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import * as Utils from 'app/shared/util/utils';
import { ExerciseDetailDirective } from 'app/shared/detail-overview-list/exercise-detail.directive';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamDetailComponent', () => {
    let fixture: ComponentFixture<ExamDetailComponent>;
    let component: ExamDetailComponent;
    let service: ExamManagementService;
    let router: Router;

    const exampleHTML = '<h1>Sample Markdown</h1>';
    const exam = new Exam();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    {
                        path: 'course-management/:courseId/exams/:examId/assessment-dashboard',
                        component: DummyComponent,
                    },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/student-exams', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                    { path: 'course-management/:courseId/exams', component: DummyComponent },
                ]),
                ExerciseDetailDirective,
                MockComponent(NoDataComponent),
                FaIconComponent,
            ],
            declarations: [
                DetailOverviewListComponent,
                ExamDetailComponent,
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                ExamChecklistComponent,
                ChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockComponent(CourseExamArchiveButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockDirective(FeatureToggleLinkDirective),
                ExamEditWorkingTimeComponent,
                MockComponent(ExamLiveAnnouncementCreateButtonComponent),
                MockDirective(ExerciseDetailDirective),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: {
                            subscribe: (fn: (value: Data) => void) =>
                                fn({
                                    exam,
                                }),
                        },
                        snapshot: {},
                    },
                },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(ArtemisMarkdownService, {
                    safeHtmlForMarkdown: () => exampleHTML,
                }),
                MockProvider(AlertService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ArtemisDurationFromSecondsPipe),
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamDetailComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ExamManagementService);
            });

        router = TestBed.inject(Router);
    });

    beforeEach(() => {
        // reset exam
        exam.id = 1;
        exam.course = new Course();
        exam.course.isAtLeastInstructor = true;
        exam.course.isAtLeastEditor = true;
        exam.course.id = 1;
        exam.title = 'Example Exam';
        exam.numberOfExamUsers = 3;
        exam.examMaxPoints = 100;
        exam.exerciseGroups = [];
        component.exam = exam;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load exam from route and display it to user', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.examDetailSections).toBeDefined();
        expect(fixture.debugElement.nativeElement.innerHTML).toInclude(exam.title!);
    });

    it('should correctly route to edit subpage', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const editButton = fixture.debugElement.query(By.css('#editButton')).nativeElement;
        editButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/edit');
        });
    }));

    it('should correctly route to student exams subpage', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const studentExamsButton = fixture.debugElement.query(By.css('#studentExamsButton')).nativeElement;
        studentExamsButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/student-exams');
        });
    }));

    it('should correctly route to dashboard', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const dashboardButton = fixture.debugElement.query(By.css('#assessment-dashboard-button')).nativeElement;
        dashboardButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/assessment-dashboard');
        });
    }));

    it('should correctly route to exercise groups', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const dashboardButton = fixture.debugElement.query(By.css('#exercises-button-groups')).nativeElement;
        dashboardButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/exercise-groups');
        });
    }));

    it('should correctly route to scores', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const scoresButton = fixture.debugElement.query(By.css('#scores-button')).nativeElement;
        scoresButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/scores');
        });
    }));

    it('should correctly route to students', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const studentsButton = fixture.debugElement.query(By.css('#students-button')).nativeElement;
        studentsButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/students');
        });
    }));

    it('should correctly route to test runs', fakeAsync(() => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const studentsButton = fixture.debugElement.query(By.css('#testrun-button')).nativeElement;
        studentsButton.click();
        discardPeriodicTasks();
        fixture.whenStable().then(() => {
            expect(location.path()).toBe('/course-management/1/exams/1/test-runs');
        });
    }));

    it('should return general routes correctly', () => {
        const route = component.getExamRoutesByIdentifier('edit');
        expect(JSON.stringify(route)).toEqual(JSON.stringify(['/course-management', exam.course!.id, 'exams', exam.id, 'edit']));
    });

    it('should reset an exam when reset exam is called', () => {
        const alertService = TestBed.inject(AlertService);

        // GIVEN
        component.exam = Object.assign({}, exam, { studentExams: [{ id: 1, numberOfExamSessions: 0 }] });
        const responseFakeReset = { body: exam } as HttpResponse<Exam>;
        jest.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        jest.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        const alertSpy = jest.spyOn(alertService, 'success').mockImplementation();

        // WHEN
        component.resetExam();

        // THEN
        expect(service.reset).toHaveBeenCalledOnce();
        expect(component.exam).toEqual(exam);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.reset.success');
    });

    it('should delete an exam when delete exam is called', () => {
        // GIVEN
        component.exam = exam;
        const responseFakeDelete = {} as HttpResponse<any[]>;
        const responseFakeEmptyExamArray = { body: [exam] } as HttpResponse<Exam[]>;
        jest.spyOn(service, 'delete').mockReturnValue(of(responseFakeDelete));
        jest.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeEmptyExamArray));
        jest.spyOn(router, 'navigate');

        // WHEN
        component.deleteExam(exam.id!);

        // THEN
        expect(service.delete).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledOnce();
    });

    it('should call scrollToTopOfPage on component initialization', () => {
        const scrollToTopOfPageSpy = jest.spyOn(Utils, 'scrollToTopOfPage');
        component.ngOnInit();
        expect(scrollToTopOfPageSpy).toHaveBeenCalled();
        scrollToTopOfPageSpy.mockRestore();
    });
});
