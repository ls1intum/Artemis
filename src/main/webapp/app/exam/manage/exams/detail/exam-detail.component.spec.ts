import { Location } from '@angular/common';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Data, Router, RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ChecklistCheckComponent } from 'app/ui/components/checklist-check/checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { ProgressBarComponent } from 'app/ui/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CourseExamArchiveButtonComponent } from 'app/ui/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { DeleteButtonDirective } from 'app/ui/delete-dialog/directive/delete-button.directive';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { DetailOverviewListComponent } from 'app/ui/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import * as Utils from 'app/foundation/util/utils';
import { ExerciseDetailDirective } from 'app/ui/detail-overview-list/exercise-detail.directive';
import { NoDataComponent } from 'app/ui/components/no-data/no-data-component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

@Component({
    template: '',
})
class DummyComponent {}

describe('ExamDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExamDetailComponent>;
    let component: ExamDetailComponent;
    let service: ExamManagementService;
    let router: Router;

    const exampleHTML = '<h1>Sample Markdown</h1>';
    const exam = new Exam();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([
                    { path: 'course-management/:courseId/exams/:examId/edit', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/exercise-groups', component: DummyComponent },
                    {
                        path: 'course-management/:courseId/exams/:examId/assessment-dashboard',
                        component: DummyComponent,
                    },
                    { path: 'course-management/:courseId/exams/:examId/scores', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/test-runs', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/students', component: DummyComponent },
                    { path: 'course-management/:courseId/exams', component: DummyComponent },
                ]),
                MockComponent(NoDataComponent),
                FaIconComponent,
                DetailOverviewListComponent,
                ExamDetailComponent,
                ExamChecklistComponent,
                ChecklistCheckComponent,
                ExamChecklistExerciseGroupTableComponent,
                ProgressBarComponent,
                MockComponent(CourseExamArchiveButtonComponent),
                ExamEditWorkingTimeComponent,
                MockComponent(ExamLiveAnnouncementCreateButtonComponent),
                DummyComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockDirective(FeatureToggleLinkDirective),
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
                { provide: DialogService, useClass: MockDialogService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamDetailComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExamManagementService);

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
        vi.restoreAllMocks();
    });

    it('should load exam from route and display it to user', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.examDetailSections).toBeDefined();
        expect(fixture.debugElement.nativeElement.innerHTML).toContain(exam.title!);
    });

    it('should correctly route to edit subpage', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const editButton = fixture.debugElement.query(By.css('#editButton')).nativeElement;
        editButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/edit');
    });

    it('should correctly route to dashboard', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const dashboardButton = fixture.debugElement.query(By.css('#assessment-dashboard-button')).nativeElement;
        dashboardButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/assessment-dashboard');
    });

    it('should correctly route to exercise groups', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const dashboardButton = fixture.debugElement.query(By.css('#exercises-button-groups')).nativeElement;
        dashboardButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/exercise-groups');
    });

    it('should correctly route to scores', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const scoresButton = fixture.debugElement.query(By.css('#scores-button')).nativeElement;
        scoresButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/scores');
    });

    it('should correctly route to students', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const studentsButton = fixture.debugElement.query(By.css('#students-button')).nativeElement;
        studentsButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/students');
    });

    it('should correctly route to test runs', async () => {
        const location = TestBed.inject(Location);
        fixture.detectChanges();
        const studentsButton = fixture.debugElement.query(By.css('#testrun-button')).nativeElement;
        studentsButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/test-runs');
    });

    it('should return general routes correctly', () => {
        const route = component.getExamRoutesByIdentifier('edit');
        expect(JSON.stringify(route)).toEqual(JSON.stringify(['/course-management', exam.course!.id, 'exams', exam.id, 'edit']));
    });

    it('should reset an exam when reset exam is called', () => {
        const alertService = TestBed.inject(AlertService);

        // GIVEN
        component.exam = { ...exam, studentExams: [{ id: 1, numberOfExamSessions: 0 }] };
        const responseFakeReset = { body: exam } as HttpResponse<Exam>;
        vi.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        vi.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        const alertSpy = vi.spyOn(alertService, 'success').mockImplementation(() => undefined as any);

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
        const responseFakeDelete = new HttpResponse<void>({ status: 200 });
        const responseFakeEmptyExamArray = { body: [exam] } as HttpResponse<Exam[]>;
        vi.spyOn(service, 'delete').mockReturnValue(of(responseFakeDelete));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeEmptyExamArray));
        vi.spyOn(router, 'navigate');

        // WHEN
        component.deleteExam(exam.id!);

        // THEN
        expect(service.delete).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledOnce();
    });

    it('should call scrollToTopOfPage on component initialization', () => {
        const scrollToTopOfPageSpy = vi.spyOn(Utils, 'scrollToTopOfPage');
        component.ngOnInit();
        expect(scrollToTopOfPageSpy).toHaveBeenCalled();
        scrollToTopOfPageSpy.mockRestore();
    });
});
