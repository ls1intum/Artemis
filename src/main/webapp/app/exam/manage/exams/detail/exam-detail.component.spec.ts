import { Location } from '@angular/common';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Data, Router, RouterModule } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ChecklistCheckComponent } from 'app/shared-ui/components/checklist-check/checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { ProgressBarComponent } from 'app/exercise/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { CourseExamArchiveButtonComponent } from 'app/shared-ui/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { DetailOverviewListComponent } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import * as Utils from 'app/foundation/util/utils';
import { ExerciseDetailDirective } from 'app/shared-ui/detail-overview-list/exercise-detail.directive';
import { NoDataComponent } from 'app/shared-ui/components/no-data/no-data-component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExamDeletionSummaryDTO } from 'app/exam/shared/entities/exam-deletion-summary.model';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([
                    { path: 'course-management/:courseId/exams/:examId/bonus', component: DummyComponent },
                    { path: 'course-management/:courseId/exams/:examId/plagiarism-cases', component: DummyComponent },
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
        exam.testExam = false;
        exam.examiner = undefined;
        exam.gracePeriod = undefined;
        component.exam.set(exam);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load exam from route and display it to user', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.examDetailSections()).toBeDefined();
        expect(fixture.debugElement.nativeElement.innerHTML).toContain(exam.title!);
    });

    it('should correctly route to bonus', async () => {
        const location = TestBed.inject(Location);
        component.canHaveBonus.set(true);
        fixture.detectChanges();
        const bonusButton = fixture.debugElement.query(By.css('a[href="/course-management/1/exams/1/bonus"]')).nativeElement;
        bonusButton.click();
        await fixture.whenStable();
        expect(location.path()).toBe('/course-management/1/exams/1/bonus');
    });

    describe('header and checklist actions', () => {
        const bonusLink = 'a[href="/course-management/1/exams/1/bonus"]';
        const plagiarismCasesLink = 'a[href="/course-management/1/exams/1/plagiarism-cases"]';
        const gradingScaleUrl = '/api/assessment/courses/1/exams/1/grading-scale';

        const loadGradingScale = (gradeType: GradeType) => {
            const request = TestBed.inject(HttpTestingController).expectOne((req) => req.url.endsWith('/exams/1/grading-scale'));
            request.flush({ gradeSteps: { gradeType } });
        };
        const element = () => fixture.debugElement.nativeElement as HTMLElement;
        const activatePlagiarismModule = () =>
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_PLAGIARISM] } as ProfileInfo);

        it('should request the grading scale of the exam when loading it', () => {
            fixture.detectChanges();

            const request = TestBed.inject(HttpTestingController).expectOne((req) => req.url.endsWith('/exams/1/grading-scale'));
            expect(request.request.method).toBe('GET');
            expect(request.request.url).toContain(gradingScaleUrl.replace('/api/assessment', ''));
            request.flush({ gradeSteps: { gradeType: GradeType.GRADE } });
            expect(component.canHaveBonus()).toBe(true);
        });

        it('should offer the plagiarism cases only as a step of the exam checklist and not as a button of its own', () => {
            fixture.detectChanges();

            const links = Array.from(element().querySelectorAll(plagiarismCasesLink));
            expect(links).toHaveLength(1);
            expect(links[0].closest('jhi-exam-checklist')).not.toBeNull();
            // the step sits in the correction table of the checklist, not in the button row of the header
            expect(links[0].closest('tr')!.querySelector('td')!.textContent!.trim()).toBe('3');
            expect(links[0].closest('table')).not.toBeNull();
        });

        it('should navigate to the plagiarism cases through the checklist step', async () => {
            activatePlagiarismModule();
            const location = TestBed.inject(Location);
            fixture.detectChanges();

            const link = element().querySelector(plagiarismCasesLink) as HTMLElement;
            expect(link.closest('jhi-feature-overlay')!.querySelector('.pe-none')).toBeNull();
            link.click();
            await fixture.whenStable();

            expect(location.path()).toBe('/course-management/1/exams/1/plagiarism-cases');
        });

        it('should disable the plagiarism cases step when the plagiarism module is not active', () => {
            fixture.detectChanges();

            const link = element().querySelector(plagiarismCasesLink)!;
            expect(link.closest('jhi-feature-overlay')!.querySelector('.pe-none.opacity-50')).not.toBeNull();
        });

        it.each([
            { gradeType: GradeType.GRADE, bonusShown: true },
            { gradeType: GradeType.BONUS, bonusShown: false },
            { gradeType: GradeType.NONE, bonusShown: false },
        ])('should show the bonus button for instructors only for a grading scale of type GRADE (type: $gradeType)', ({ gradeType, bonusShown }) => {
            fixture.detectChanges();
            loadGradingScale(gradeType);
            fixture.detectChanges();

            expect(component.canHaveBonus()).toBe(bonusShown);
            const bonus = element().querySelector(bonusLink);
            expect(bonus !== null).toBe(bonusShown);
            const separator = element().querySelector('hr')!;
            if (bonusShown) {
                expect(separator.previousElementSibling).toBe(bonus!.parentElement);
                expect(bonus!.parentElement!.children).toHaveLength(1);
            } else {
                // no empty button row is rendered in front of the separator
                expect(separator.previousElementSibling).toBeNull();
            }
            // the checklist is not affected by the bonus button
            expect(element().querySelector(plagiarismCasesLink)).not.toBeNull();
        });

        it('should not request or show a bonus button when the exam has no grading scale', () => {
            fixture.detectChanges();
            TestBed.inject(HttpTestingController)
                .expectOne((req) => req.url.endsWith('/exams/1/grading-scale'))
                .flush(null);
            fixture.detectChanges();

            expect(component.canHaveBonus()).toBe(false);
            expect(element().querySelector(bonusLink)).toBeNull();
        });

        it('should show neither the bonus button nor the checklist to a user who is not an instructor', () => {
            exam.course!.isAtLeastInstructor = false;
            fixture.detectChanges();
            loadGradingScale(GradeType.GRADE);
            fixture.detectChanges();

            expect(component.canHaveBonus()).toBe(true);
            expect(element().querySelector(bonusLink)).toBeNull();
            expect(element().querySelector(plagiarismCasesLink)).toBeNull();
            expect(element().querySelector('jhi-exam-checklist')).toBeNull();
            // the page itself is still rendered, so the missing links are caused by the role
            expect(element().querySelector('jhi-detail-overview-list')).not.toBeNull();
            expect(element().querySelector('hr')!.previousElementSibling).toBeNull();
        });
    });

    it('should return general routes correctly', () => {
        const route = component.getExamRoutesByIdentifier('edit');
        expect(JSON.stringify(route)).toEqual(JSON.stringify(['/course-management', exam.course!.id, 'exams', exam.id, 'edit']));
    });

    it('should reset an exam when reset exam is called', () => {
        const alertService = TestBed.inject(AlertService);

        // GIVEN
        component.exam.set({ ...exam, studentExams: [{ id: 1, numberOfExamSessions: 0 }] });
        const responseFakeReset = { body: exam } as HttpResponse<Exam>;
        vi.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        vi.spyOn(service, 'reset').mockReturnValue(of(responseFakeReset));
        const alertSpy = vi.spyOn(alertService, 'success').mockImplementation(() => undefined as any);

        // WHEN
        component.resetExam();

        // THEN
        expect(service.reset).toHaveBeenCalledOnce();
        expect(component.exam()).toEqual(exam);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.reset.success');
    });

    it('should delete an exam when delete exam is called', () => {
        const eventManager = TestBed.inject(EventManager);
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');

        // GIVEN
        component.exam.set(exam);
        const responseFakeDelete = new HttpResponse<void>({ status: 200 });
        const responseFakeEmptyExamArray = { body: [exam] } as HttpResponse<Exam[]>;
        vi.spyOn(service, 'delete').mockReturnValue(of(responseFakeDelete));
        vi.spyOn(service, 'findAllExamsForCourse').mockReturnValue(of(responseFakeEmptyExamArray));
        vi.spyOn(router, 'navigate');

        // WHEN
        component.deleteExam(exam.id!);

        // THEN
        expect(service.delete).toHaveBeenCalledOnce();
        expect(broadcastSpy).toHaveBeenCalledOnce();
        expect(broadcastSpy).toHaveBeenCalledWith({ name: 'examListModification', content: 'dummy' });
        expect(router.navigate).toHaveBeenCalledOnce();
    });

    it('should publish the error message to the reset dialog when resetting the exam fails', () => {
        const dialogErrors: string[] = [];
        component.dialogError$.subscribe((message) => dialogErrors.push(message));
        const error = new HttpErrorResponse({ status: 500, statusText: 'Server Error', url: '/api/exam/reset' });
        vi.spyOn(service, 'reset').mockReturnValue(throwError(() => error));
        const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'success');

        component.resetExam();

        expect(dialogErrors).toEqual([error.message]);
        expect(error.message).toContain('500');
        expect(alertSpy).not.toHaveBeenCalled();
    });

    it('should clear the dialog error and show the reset exam after a successful reset', () => {
        const dialogErrors: string[] = [];
        component.dialogError$.subscribe((message) => dialogErrors.push(message));
        const resetExam = Object.assign(new Exam(), { id: 1, title: 'Reset Exam', course: exam.course });
        vi.spyOn(service, 'reset').mockReturnValue(of({ body: resetExam } as HttpResponse<Exam>));

        component.resetExam();

        expect(dialogErrors).toEqual(['']);
        expect(component.exam()).toBe(resetExam);
        expect(service.reset).toHaveBeenCalledWith(1, 1);
    });

    it('should publish the error message to the delete dialog and stay on the page when deleting the exam fails', () => {
        const dialogErrors: string[] = [];
        component.dialogError$.subscribe((message) => dialogErrors.push(message));
        const error = new HttpErrorResponse({ status: 403, statusText: 'Forbidden', url: '/api/exam/delete' });
        vi.spyOn(service, 'delete').mockReturnValue(throwError(() => error));
        const navigateSpy = vi.spyOn(router, 'navigate');
        const broadcastSpy = vi.spyOn(TestBed.inject(EventManager), 'broadcast');

        component.deleteExam(1);

        expect(service.delete).toHaveBeenCalledWith(1, 1);
        expect(dialogErrors).toEqual([error.message]);
        expect(navigateSpy).not.toHaveBeenCalled();
        expect(broadcastSpy).not.toHaveBeenCalled();
    });

    it('should navigate to the exam list of the course after deleting the exam', () => {
        vi.spyOn(service, 'delete').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        component.deleteExam(1);

        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', 1, 'exams']);
    });

    it('should combine the exam contents and the server numbers in the deletion summary', () => {
        exam.testExam = true;
        exam.course!.testCourse = false;
        exam.exerciseGroups = [
            {
                id: 1,
                exercises: [
                    { id: 1, type: ExerciseType.PROGRAMMING, numberOfParticipations: 4 },
                    { id: 2, type: ExerciseType.PROGRAMMING, numberOfParticipations: 2 },
                    { id: 3, type: ExerciseType.TEXT },
                ],
            },
            { id: 2, exercises: [{ id: 4, type: ExerciseType.QUIZ }, { id: 5 }] },
        ] as ExerciseGroup[];
        component.exam.set(exam);
        const dto: ExamDeletionSummaryDTO = {
            numberOfBuilds: 11,
            numberOfCommunicationPosts: 12,
            numberOfAnswerPosts: 13,
            numberRegisteredStudents: 14,
            numberNotStartedExams: 15,
            numberStartedExams: 16,
            numberSubmittedExams: 17,
        };
        vi.spyOn(service, 'getDeletionSummary').mockReturnValue(of({ body: dto } as HttpResponse<ExamDeletionSummaryDTO>));
        let summary: EntitySummary | undefined;

        component.examDeletionSummary().subscribe((value) => (summary = value));

        expect(service.getDeletionSummary).toHaveBeenCalledWith(1, 1);
        expect(summary).toEqual({
            'artemisApp.examManagement.delete.summary.numberExerciseGroups': 2,
            'artemisApp.examManagement.delete.summary.numberProgrammingExercises': 2,
            'artemisApp.examManagement.delete.summary.numberModelingExercises': undefined,
            'artemisApp.examManagement.delete.summary.numberTextExercises': 1,
            'artemisApp.examManagement.delete.summary.numberFileUploadExercises': undefined,
            'artemisApp.examManagement.delete.summary.numberQuizExercises': 1,
            'artemisApp.examManagement.delete.summary.numberRepositories': 6,
            'artemisApp.examManagement.delete.summary.isTestExam': true,
            'artemisApp.examManagement.delete.summary.isTestCourse': false,
            'artemisApp.examManagement.delete.summary.numberBuilds': 11,
            'artemisApp.examManagement.delete.summary.numberRegisteredStudents': 14,
            'artemisApp.examManagement.delete.summary.numberNotStartedExams': 15,
            'artemisApp.examManagement.delete.summary.numberStartedExams': 16,
            'artemisApp.examManagement.delete.summary.numberSubmittedExams': 17,
            'artemisApp.examManagement.delete.summary.numberCommunicationPosts': 12,
            'artemisApp.examManagement.delete.summary.numberAnswerPosts': 13,
        });
    });

    it('should return an empty deletion summary when the server sends no summary', () => {
        vi.spyOn(service, 'getDeletionSummary').mockReturnValue(of({ body: null } as HttpResponse<ExamDeletionSummaryDTO>));
        let summary: EntitySummary | undefined;

        component.examDeletionSummary().subscribe((value) => (summary = value));

        expect(summary).toEqual({});
    });

    it('should describe the general information of the exam in the detail sections', () => {
        exam.examiner = 'Prof. Example';
        exam.gracePeriod = 180;
        exam.numberOfExamUsers = 3;
        fixture.detectChanges();

        const sections = component.examDetailSections();
        expect(sections).toHaveLength(1);
        expect(sections[0].headline).toBe('artemisApp.exam.detail.sections.general');
        const details = sections[0].details as { title: string; data: Record<string, unknown> }[];
        const byTitle = (title: string) => details.find((detail) => detail.title === title)!.data;
        expect(byTitle('artemisApp.exam.title')['text']).toBe('Example Exam');
        expect(byTitle('artemisApp.examManagement.examiner')['text']).toBe('Prof. Example');
        expect(byTitle('artemisApp.examManagement.gracePeriod')['text']).toBe(180);
        expect(byTitle('artemisApp.examManagement.examStudents.registeredStudents')['text']).toBe(3);
        expect(byTitle('artemisApp.exam.course')['routerLink']).toEqual(['/course-management', 1]);
        expect(byTitle('artemisApp.examManagement.startText')['innerHtml']).toBe(exampleHTML);
    });

    it('should call scrollToTopOfPage on component initialization', () => {
        const scrollToTopOfPageSpy = vi.spyOn(Utils, 'scrollToTopOfPage');
        component.ngOnInit();
        expect(scrollToTopOfPageSpy).toHaveBeenCalled();
        scrollToTopOfPageSpy.mockRestore();
    });
});
