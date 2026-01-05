import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizExerciseManageButtonsComponent } from 'app/quiz/manage/manage-buttons/quiz-exercise-manage-buttons.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProvider } from 'ng-mocks';

describe('QuizExercise Management Buttons Component', () => {
    setupTestBed({ zoneless: true });

    let comp: QuizExerciseManageButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseManageButtonsComponent>;
    let quizExerciseService: QuizExerciseService;
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
        vi.spyOn(quizExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.deleteQuizExercise();
        expect(quizExerciseService.delete).toHaveBeenCalledWith(456);
        expect(quizExerciseService.delete).toHaveBeenCalledOnce();
    });

    it('should export quiz', () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );
        vi.spyOn(quizExerciseService, 'exportQuiz');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.exportQuizExercise(true);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], true, 'Quiz Exercise');
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledOnce();
    });

    it('should export quiz with exportAll false', () => {
        vi.spyOn(quizExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                }),
            ),
        );
        vi.spyOn(quizExerciseService, 'exportQuiz');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();
        comp.exportQuizExercise(false);
        expect(quizExerciseService.exportQuiz).toHaveBeenCalledWith([], false, 'Quiz Exercise');
    });

    it('should evaluate quiz exercise successfully', () => {
        vi.spyOn(exerciseService, 'evaluateQuizExercise').mockReturnValue(
            of(
                new HttpResponse<void>({
                    status: 200,
                }),
            ),
        );
        const alertService = TestBed.inject(AlertService);
        const successSpy = vi.spyOn(alertService, 'success');

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        expect(comp.isEvaluatingQuizExercise).toBeFalsy();
        comp.evaluateQuizExercise();

        expect(exerciseService.evaluateQuizExercise).toHaveBeenCalledWith(456);
        expect(successSpy).toHaveBeenCalledWith('artemisApp.quizExercise.evaluateQuizExerciseSuccess');
        expect(comp.isEvaluatingQuizExercise).toBe(false);
    });

    it('should handle evaluate quiz exercise error', () => {
        const errorResponse = new HttpErrorResponse({ error: 'Error', status: 500, statusText: 'Server Error' });
        vi.spyOn(exerciseService, 'evaluateQuizExercise').mockReturnValue(throwError(() => errorResponse));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.evaluateQuizExercise();

        expect(exerciseService.evaluateQuizExercise).toHaveBeenCalledWith(456);
        expect(comp.isEvaluatingQuizExercise).toBe(false);
    });

    it('should handle delete quiz exercise error', () => {
        const errorResponse = new HttpErrorResponse({ error: 'Error', status: 500, statusText: 'Server Error' });
        vi.spyOn(quizExerciseService, 'delete').mockReturnValue(throwError(() => errorResponse));

        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        expect(quizExerciseService.delete).toHaveBeenCalledWith(456);
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
        expect(comp.faTable).toBeDefined();
        expect(comp.faFileExport).toBeDefined();
        expect(comp.faWrench).toBeDefined();
        expect(comp.faTrash).toBeDefined();
        expect(comp.faListAlt).toBeDefined();
        expect(comp.faUndo).toBeDefined();
        expect(comp.faClipboardCheck).toBeDefined();
    });

    it('should have button types defined', () => {
        expect(comp.ButtonType).toBeDefined();
        expect(comp.ButtonSize).toBeDefined();
    });

    it('should initialize course base URL', () => {
        fixture.componentRef.setInput('quizExercise', quizExercise);
        comp.ngOnInit();

        expect(comp.courseId).toBe(123);
        expect(comp.isExamMode).toBeFalsy();
        expect(comp.baseUrl).toBe('/course-management/123');
    });
});

describe('QuizExercise Management Buttons Component - Exam Mode', () => {
    setupTestBed({ zoneless: true });

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
        expect(comp.baseUrl).toBe('/course-management/123/exams/789/exercise-groups/111');
    });

    it('should navigate after delete in detail page', () => {
        const quizExerciseService = TestBed.inject(QuizExerciseService);
        const router = TestBed.inject(Router);

        vi.spyOn(quizExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

        fixture.componentRef.setInput('quizExercise', quizExercise);
        fixture.componentRef.setInput('isDetailPage', true);
        comp.ngOnInit();

        comp.deleteQuizExercise();

        expect(quizExerciseService.delete).toHaveBeenCalledWith(456);
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 123, 'exercises']);
    });
});
