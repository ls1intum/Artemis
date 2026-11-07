import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExerciseManageButtonsComponent } from 'app/quiz/manage/manage-buttons/quiz-exercise-manage-buttons.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { CreateVariantWithAiButtonComponent } from 'app/hyperion/variants/create-variant-with-ai-button.component';
import { DialogService } from 'primeng/dynamicdialog';
import { QuizExerciseDeletionApi } from 'app/openapi/api/quiz-exercise-deletion-api';
import { QuizExerciseEvaluationApi } from 'app/openapi/api/quiz-exercise-evaluation-api';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { MockEntityDeletionService } from 'test/helpers/mocks/service/mock-entity-deletion.service';

describe('QuizExercise Management Buttons Component', () => {
    let comp: QuizExerciseManageButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseManageButtonsComponent>;
    let quizExerciseService: QuizExerciseService;
    let quizExerciseDeletionApi: QuizExerciseDeletionApi;
    let exerciseService: ExerciseService;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    quizExercise.quizQuestions = [];
    const quizBatch = new QuizBatch();
    quizBatch.id = 567;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        })
            .overrideTemplate(QuizExerciseManageButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseManageButtonsComponent);
        comp = fixture.componentInstance;
        quizExerciseService = TestBed.inject(QuizExerciseService);
        quizExerciseDeletionApi = TestBed.inject(QuizExerciseDeletionApi);
        exerciseService = TestBed.inject(ExerciseService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reset quiz', () => {
        vi.spyOn(exerciseService, 'reset').mockReturnValue(
            of(
                new HttpResponse({
                    body: undefined,
                }),
            ),
        );
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.resetQuizExercise();
        expect(exerciseService.reset).toHaveBeenCalledWith(456);
        expect(exerciseService.reset).toHaveBeenCalledOnce();
    });

    it('should delete quiz', () => {
        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(of(undefined));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.deleteQuizExercise();
        expect(quizExerciseDeletionApi.deleteQuizExercise).toHaveBeenCalledWith(456);
        expect(quizExerciseDeletionApi.deleteQuizExercise).toHaveBeenCalledOnce();
    });

    it('should export quiz', () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(quizExercise));
        vi.spyOn(quizExerciseService, 'exportQuiz');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.exportQuizExercise(true);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], true, 'Quiz Exercise');
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledOnce();
    });

    it('should export quiz with exportAll false', () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(quizExercise));
        vi.spyOn(quizExerciseService, 'exportQuiz');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.exportQuizExercise(false);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], false, 'Quiz Exercise');
    });

    it('should evaluate quiz exercise successfully', () => {
        vi.spyOn(TestBed.inject(QuizExerciseEvaluationApi), 'evaluateQuizExercise').mockReturnValue(of(undefined));
        const alertService = TestBed.inject(AlertService);
        const successSpy = vi.spyOn(alertService, 'success');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        expect(comp.isEvaluatingQuizExercise()).toBeFalsy();
        comp.evaluateQuizExercise();

        expect(TestBed.inject(QuizExerciseEvaluationApi).evaluateQuizExercise).toHaveBeenCalledWith(456);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.quizExercise.evaluateQuizExerciseSuccess');
        expect(comp.isEvaluatingQuizExercise()).toBe(false);
    });

    it('should handle evaluate quiz exercise error', () => {
        const errorResponse = new HttpErrorResponse({ error: 'Error', status: 500, statusText: 'Server Error' });
        vi.spyOn(TestBed.inject(QuizExerciseEvaluationApi), 'evaluateQuizExercise').mockReturnValue(throwError(() => errorResponse));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.evaluateQuizExercise();

        expect(TestBed.inject(QuizExerciseEvaluationApi).evaluateQuizExercise).toHaveBeenCalledWith(456);
        expect(comp.isEvaluatingQuizExercise()).toBe(false);
    });

    it('should handle delete quiz exercise error', () => {
        const errorResponse = new HttpErrorResponse({ error: 'Error', status: 500, statusText: 'Server Error' });
        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(throwError(() => errorResponse));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        expect(quizExerciseDeletionApi.deleteQuizExercise).toHaveBeenCalledWith(456);
    });

    it('should handle reset quiz exercise error', () => {
        const errorResponse = new HttpErrorResponse({ error: 'Error', status: 500, statusText: 'Server Error' });
        vi.spyOn(exerciseService, 'reset').mockReturnValue(throwError(() => errorResponse));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.resetQuizExercise();

        expect(exerciseService.reset).toHaveBeenCalledWith(456);
    });

    it('should have icons defined', () => {
        expect(comp.faEye).toBeDefined();
        expect(comp.faSignal).toBeDefined();
        expect(comp.faFileExport).toBeDefined();
        expect(comp.faWrench).toBeDefined();
        expect(comp.faTrash).toBeDefined();
        expect(comp.faListAlt).toBeDefined();
        expect(comp.faUndo).toBeDefined();
        expect(comp.faClipboardCheck).toBeDefined();
    });

    it('should initialize course base URL', () => {
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        expect(comp.courseId).toBe(123);
        expect(comp.isExamMode).toBeFalsy();
        expect(comp.baseUrl()).toBe('/course-management/123');
    });
});

describe('QuizExercise Management Buttons Component - Exam Mode', () => {
    let comp: QuizExerciseManageButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseManageButtonsComponent>;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    quizExercise.quizQuestions = [];

    const examRoute = {
        snapshot: {
            paramMap: convertToParamMap({
                courseId: course.id,
                examId: 789,
                exerciseGroupId: 111,
            }),
        },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: examRoute },
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(Router),
                provideHttpClient(),
            ],
        })
            .overrideTemplate(QuizExerciseManageButtonsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseManageButtonsComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize in exam mode', () => {
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        expect(comp.courseId).toBe(123);
        expect(comp.examId).toBe(789);
        expect(comp.isExamMode).toBe(true);
        expect(comp.baseUrl()).toBe('/course-management/123/exams/789/exercise-groups/111');
    });

    it('exports an exam quiz through the exam details endpoint', () => {
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        const service = TestBed.inject(QuizExerciseService);
        const examFind = vi.spyOn(service, 'findForExam').mockReturnValue(of(quizExercise));
        const courseFind = vi.spyOn(service, 'find');
        const exportSpy = vi.spyOn(service, 'exportQuiz').mockImplementation(() => {});
        comp.exportQuizExercise(true);
        expect(examFind).toHaveBeenCalledExactlyOnceWith(quizExercise.id);
        expect(courseFind).not.toHaveBeenCalled();
        expect(exportSpy).toHaveBeenCalledExactlyOnceWith([], true, quizExercise.title);
    });

    it('should navigate after delete in detail page', () => {
        const quizExerciseDeletionApi = TestBed.inject(QuizExerciseDeletionApi);
        const router = TestBed.inject(Router);

        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(of(undefined));
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        fixture.componentRef.setInput('quizExercise', quizExercise);
        fixture.componentRef.setInput('isDetailPage', true);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        expect(quizExerciseDeletionApi.deleteQuizExercise).toHaveBeenCalledWith(456);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 123, 'exercises']);
    });

    it('should not navigate when the deletion ends while the reused component shows another quiz', () => {
        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        const ended$ = new Subject<EntityDeletionProgress>();
        deletionService.delete.mockReturnValueOnce(ended$);
        const quizExerciseDeletionApi = TestBed.inject(QuizExerciseDeletionApi);
        const router = TestBed.inject(Router);
        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(of(undefined));
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.componentRef.setInput('quizExercise', quizExercise);
        fixture.componentRef.setInput('isDetailPage', true);
        comp.ngOnInit();
        comp.deleteQuizExercise();

        fixture.componentRef.setInput('quizExercise', { ...quizExercise, id: 999 });
        ended$.next({
            entityType: EntityDeletionType.EXERCISE,
            entityId: quizExercise.id!,
            status: EntityDeletionStatus.COMPLETED,
            stepsCompleted: 1,
            totalSteps: 1,
            itemsProcessed: 0,
            totalItems: 0,
            weightedProgressPercent: 100,
        });

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should stay on the detail page when the background deletion failed', () => {
        (TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService).status = EntityDeletionStatus.FAILED;
        const quizExerciseDeletionApi = TestBed.inject(QuizExerciseDeletionApi);
        const router = TestBed.inject(Router);
        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(of(undefined));
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        fixture.componentRef.setInput('quizExercise', quizExercise);
        fixture.componentRef.setInput('isDetailPage', true);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should hand the quiz to the background deletion', () => {
        const quizExerciseDeletionApi = TestBed.inject(QuizExerciseDeletionApi);
        vi.spyOn(quizExerciseDeletionApi, 'deleteQuizExercise').mockReturnValue(of(undefined));
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        expect(deletionService.delete).toHaveBeenCalledWith(
            expect.objectContaining({ entityType: EntityDeletionType.EXERCISE, entityId: 456, successAlertKey: 'artemisApp.quizExercise.deleted' }),
            expect.anything(),
        );
    });
});

describe('QuizExercise Management Buttons Component evaluate button', () => {
    let fixture: ComponentFixture<QuizExerciseManageButtonsComponent>;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 123 }) } } as any as ActivatedRoute;

    function render(isDetailPage: boolean, canBeEvaluated: boolean | undefined) {
        const quiz = new QuizExercise({ id: 123 } as Course, undefined);
        quiz.id = 456;
        quiz.quizEnded = true;
        quiz.isAtLeastInstructor = true;
        quiz.canBeEvaluated = canBeEvaluated;
        fixture.componentRef.setInput('quizExercise', quiz);
        fixture.componentRef.setInput('isDetailPage', isDetailPage);
        fixture.detectChanges();
        return fixture.nativeElement.querySelector('[data-testid="quiz-evaluate-button"]') as HTMLElement | null;
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                MockProvider(DialogService),
            ],
        }).overrideComponent(QuizExerciseManageButtonsComponent, {
            remove: { imports: [CreateVariantWithAiButtonComponent] },
            add: { imports: [MockComponent(CreateVariantWithAiButtonComponent)] },
        });
        fixture = TestBed.createComponent(QuizExerciseManageButtonsComponent);
    });

    it('groups the variant with editing and keeps export in a one-entry menu', async () => {
        const quiz = Object.assign(new QuizExercise({ id: 123 }, undefined), {
            id: 456,
            isAtLeastTutor: true,
            isAtLeastEditor: true,
            isEditable: true,
        });
        fixture.componentRef.setInput('quizExercise', quiz);
        fixture.componentRef.setInput('isDetailPage', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] jhi-create-variant-with-ai-button')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('jhi-create-variant-with-ai-button')).toHaveLength(1);
        fixture.nativeElement.querySelector('[data-testid="exercise-more-actions"]').click();
        await fixture.whenStable();
        expect(document.querySelectorAll('tumaet-ui-menu [tumAetUiMenuItem]')).toHaveLength(1);
        expect(document.querySelector('tumaet-ui-menu')?.textContent).toContain('entity.action.export');
    });

    it('shows reset and delete only in the instructor administration section', () => {
        render(true, true);
        fixture.componentRef.setInput('detailPageSection', 'administration');
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[id="delete-quiz-456"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelectorAll('[jhiDeleteButton]')).toHaveLength(2);
        expect(fixture.nativeElement.querySelector('jhi-exercise-detail-actions')).toBeNull();
        fixture.componentRef.setInput('quizExercise', Object.assign(new QuizExercise({ id: 123 }, undefined), { id: 456, isAtLeastTutor: true }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[jhiDeleteButton]')).toBeNull();
    });

    it('preserves the edit lock while a quiz is running', () => {
        const quiz = Object.assign(new QuizExercise({ id: 123 }, undefined), { id: 456, isAtLeastEditor: true, isEditable: false });
        fixture.componentRef.setInput('quizExercise', quiz);
        fixture.componentRef.setInput('isDetailPage', true);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] a').getAttribute('aria-disabled')).toBe('true');
        expect(fixture.nativeElement.querySelector('a[href$="/edit"]')).toBeNull();
        fixture.componentRef.setInput('quizExercise', Object.assign(new QuizExercise({ id: 123 }, undefined), { id: 456, isAtLeastEditor: true, isEditable: true }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('a[href$="/quiz-exercises/456/edit"]')).not.toBeNull();
    });

    it.each([false, true])('names compact edit and reevaluation links when the quiz has ended: %s', (quizEnded) => {
        const quiz = Object.assign(new QuizExercise({ id: 123 }, undefined), { id: 456, isAtLeastEditor: true, isAtLeastInstructor: true, isEditable: true, quizEnded });
        fixture.componentRef.setInput('quizExercise', quiz);
        fixture.componentRef.setInput('isDetailPage', true);
        fixture.detectChanges();
        const link = fixture.nativeElement.querySelector('[data-testid="exercise-editing-actions"] a') as HTMLAnchorElement;
        expect(link.getAttribute('aria-label')).toBe(quizEnded ? 'entity.action.re-evaluate' : 'entity.action.edit');
    });

    it('names the compact evaluation button independently of its visible label', () => {
        const button = render(true, true)!.querySelector('button')!;
        expect(button.getAttribute('aria-label')).toBe('artemisApp.quizExercise.evaluateQuizExercise');
        expect(button.disabled).toBe(false);
    });

    it('keeps destructive actions out of the detail toolbar', () => {
        render(true, true);
        expect(fixture.nativeElement.querySelector('[id="delete-quiz-456"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-exercise-detail-actions')).not.toBeNull();
    });

    it('keeps row deletion available without adding a duplicate variant action', () => {
        render(false, true);
        expect(fixture.nativeElement.querySelector('[id="delete-quiz-456"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('jhi-create-variant-with-ai-button')).toBeNull();
    });

    it.each([
        { status: 404, error: { title: 'Quiz no longer exists' } },
        { status: 500, error: {} },
    ])('shows evaluation failures locally for status $status, including errors without a title', async ({ status, error }) => {
        const failure = new HttpErrorResponse({ status, error });
        vi.spyOn(TestBed.inject(QuizExerciseEvaluationApi), 'evaluateQuizExercise').mockReturnValue(throwError(() => failure));
        const globalError = vi.spyOn(TestBed.inject(AlertService), 'error');
        const button = render(true, true)!;

        button.querySelector('button')!.click();
        await fixture.whenStable();

        expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(failure.message);
        expect(fixture.componentInstance.isEvaluatingQuizExercise()).toBe(false);
        expect(globalError).not.toHaveBeenCalled();

        vi.spyOn(TestBed.inject(QuizExerciseEvaluationApi), 'evaluateQuizExercise').mockReturnValue(of(undefined));
        button.querySelector('button')!.click();
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    });

    it.each([
        { status: 0, error: {} },
        { status: 403, error: {} },
        { status: 500, error: { title: 'Evaluation failed' } },
        { status: 400, error: { fieldErrors: [{ field: 'quiz', message: 'invalid' }] } },
    ])('leaves failures already handled globally to the global alert for status $status', async ({ status, error }) => {
        vi.spyOn(TestBed.inject(QuizExerciseEvaluationApi), 'evaluateQuizExercise').mockReturnValue(throwError(() => new HttpErrorResponse({ status, error })));
        const button = render(true, true)!;
        button.querySelector('button')!.click();
        await fixture.whenStable();
        expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
        expect(fixture.componentInstance.isEvaluatingQuizExercise()).toBe(false);
    });

    it('should offer the evaluate button on the detail page when the server accepts the evaluation', () => {
        expect(render(true, true)).not.toBeNull();
    });

    it('should not offer the evaluate button when the server does not accept the evaluation', () => {
        expect(render(true, false)).toBeNull();
    });

    it('should not offer the evaluate button when the server did not say that the evaluation is possible', () => {
        expect(render(true, undefined)).toBeNull();
    });

    it('should not offer the evaluate button outside of the detail page', () => {
        expect(render(false, true)).toBeNull();
    });
});
