import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizReEvaluateWarningComponent } from 'app/quiz/manage/re-evaluate/warning/quiz-re-evaluate-warning.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { QuizReEvaluateService } from 'app/quiz/manage/re-evaluate/services/quiz-re-evaluate.service';
import { QuizQuestionType, ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';

describe('QuizExercise Re-evaluate Warning Component', () => {
    setupTestBed({ zoneless: true });

    let comp: QuizReEvaluateWarningComponent;
    let fixture: ComponentFixture<QuizReEvaluateWarningComponent>;
    let quizService: QuizExerciseService;
    let quizReEvaluateService: QuizReEvaluateService;
    let activeModal: NgbActiveModal;
    let navigationUtilService: ArtemisNavigationUtilService;

    const course = { id: 123 } as Course;

    const createQuizExercise = (): QuizExercise => {
        const quizExercise = new QuizExercise(course, undefined);
        quizExercise.id = 456;
        quizExercise.title = 'MyQuiz';

        const mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.id = 1;
        mcQuestion.type = QuizQuestionType.MULTIPLE_CHOICE;
        mcQuestion.invalid = false;
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING;
        mcQuestion.answerOptions = [
            { id: 1, isCorrect: true, invalid: false },
            { id: 2, isCorrect: false, invalid: false },
        ];

        const dndQuestion = new DragAndDropQuestion();
        dndQuestion.id = 2;
        dndQuestion.type = QuizQuestionType.DRAG_AND_DROP;
        dndQuestion.invalid = false;
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY;
        dndQuestion.dragItems = [{ id: 1, invalid: false }];
        dndQuestion.dropLocations = [{ id: 1, invalid: false }];
        dndQuestion.correctMappings = [{ dragItem: { id: 1, invalid: false }, dropLocation: { id: 1, invalid: false }, invalid: false }];

        const saQuestion = new ShortAnswerQuestion();
        saQuestion.id = 3;
        saQuestion.type = QuizQuestionType.SHORT_ANSWER;
        saQuestion.invalid = false;
        saQuestion.scoringType = ScoringType.PROPORTIONAL_WITHOUT_PENALTY;
        saQuestion.solutions = [{ id: 1, invalid: false }];
        saQuestion.spots = [{ id: 1, invalid: false }];
        saQuestion.correctMappings = [{ solution: { id: 1, invalid: false }, spot: { id: 1, invalid: false }, invalid: false }];

        quizExercise.quizQuestions = [mcQuestion, dndQuestion, saQuestion];
        return quizExercise;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(NgbActiveModal),
                MockProvider(QuizReEvaluateService),
                MockProvider(ArtemisNavigationUtilService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizReEvaluateWarningComponent);
        comp = fixture.componentInstance;
        quizService = TestBed.inject(QuizExerciseService);
        quizReEvaluateService = TestBed.inject(QuizReEvaluateService);
        activeModal = TestBed.inject(NgbActiveModal);
        navigationUtilService = TestBed.inject(ArtemisNavigationUtilService);

        const quizExercise = createQuizExercise();
        comp.quizExercise = quizExercise;
        vi.spyOn(quizService, 'find').mockReturnValue(of(new HttpResponse({ body: createQuizExercise() })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize quiz exercise', () => {
        comp.ngOnInit();
        expect(comp.backUpQuiz).toBeTruthy();
        expect(comp.isSaving).toBe(false);
    });

    it('should dismiss modal on clear', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');
        comp.clear();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should detect question deleted', () => {
        comp.ngOnInit();

        // Remove a question from the current quiz
        comp.quizExercise.quizQuestions = comp.quizExercise.quizQuestions!.slice(0, 2);
        comp.loadQuizSuccess();

        expect(comp.questionDeleted).toBe(true);
    });

    it('should detect question invalid change', () => {
        comp.ngOnInit();

        // Mark a question as invalid
        comp.quizExercise.quizQuestions![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionInvalid).toBe(true);
    });

    it('should detect scoring type change', () => {
        comp.ngOnInit();

        // Change scoring type
        comp.quizExercise.quizQuestions![0].scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY;
        comp.loadQuizSuccess();

        expect(comp.scoringChanged).toBe(true);
    });

    it('should detect MC answer option deleted', () => {
        comp.ngOnInit();

        // Remove an answer option
        const mcQuestion = comp.quizExercise.quizQuestions![0] as MultipleChoiceQuestion;
        mcQuestion.answerOptions = [{ id: 1, isCorrect: true, invalid: false }];
        comp.loadQuizSuccess();

        expect(comp.questionElementDeleted).toBe(true);
    });

    it('should detect MC answer correctness changed', () => {
        comp.ngOnInit();

        // Change answer correctness
        const mcQuestion = comp.quizExercise.quizQuestions![0] as MultipleChoiceQuestion;
        mcQuestion.answerOptions![0].isCorrect = false;
        comp.loadQuizSuccess();

        expect(comp.questionCorrectness).toBe(true);
    });

    it('should detect MC answer invalid change', () => {
        comp.ngOnInit();

        // Mark answer as invalid
        const mcQuestion = comp.quizExercise.quizQuestions![0] as MultipleChoiceQuestion;
        mcQuestion.answerOptions![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionElementInvalid).toBe(true);
    });

    it('should detect DnD drag item deleted', () => {
        comp.ngOnInit();

        // Remove a drag item
        const dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
        dndQuestion.dragItems = [];
        comp.loadQuizSuccess();

        expect(comp.questionElementDeleted).toBe(true);
    });

    it('should detect DnD drop location deleted', () => {
        comp.ngOnInit();

        // Remove a drop location
        const dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
        dndQuestion.dropLocations = [];
        comp.loadQuizSuccess();

        expect(comp.questionElementDeleted).toBe(true);
    });

    it('should detect DnD correct mappings changed', () => {
        comp.ngOnInit();

        // Change correct mappings
        const dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
        dndQuestion.correctMappings = [{ dragItem: { id: 2, invalid: false }, dropLocation: { id: 2, invalid: false }, invalid: false }];
        comp.loadQuizSuccess();

        expect(comp.questionCorrectness).toBe(true);
    });

    it('should detect DnD drag item invalid change', () => {
        comp.ngOnInit();

        // Mark drag item as invalid
        const dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
        dndQuestion.dragItems![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionElementInvalid).toBe(true);
    });

    it('should detect DnD drop location invalid change', () => {
        comp.ngOnInit();

        // Mark drop location as invalid
        const dndQuestion = comp.quizExercise.quizQuestions![1] as DragAndDropQuestion;
        dndQuestion.dropLocations![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionElementInvalid).toBe(true);
    });

    it('should detect SA solution deleted', () => {
        comp.ngOnInit();

        // Remove a solution
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.solutions = [];
        comp.loadQuizSuccess();

        expect(comp.questionElementDeleted).toBe(true);
    });

    it('should detect SA spot deleted', () => {
        comp.ngOnInit();

        // Remove a spot
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.spots = [];
        comp.loadQuizSuccess();

        expect(comp.questionElementDeleted).toBe(true);
    });

    it('should detect SA solution added', () => {
        comp.ngOnInit();

        // Add a solution
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.solutions = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
        ];
        comp.loadQuizSuccess();

        expect(comp.solutionAdded).toBe(true);
    });

    it('should detect SA spot added', () => {
        comp.ngOnInit();

        // Add a spot
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.spots = [
            { id: 1, invalid: false },
            { id: 2, invalid: false },
        ];
        comp.loadQuizSuccess();

        expect(comp.solutionAdded).toBe(true);
    });

    it('should detect SA correct mappings changed', () => {
        comp.ngOnInit();

        // Change correct mappings
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.correctMappings = [{ solution: { id: 2, invalid: false }, spot: { id: 2, invalid: false }, invalid: false }];
        comp.loadQuizSuccess();

        expect(comp.questionCorrectness).toBe(true);
    });

    it('should detect SA solution invalid change', () => {
        comp.ngOnInit();

        // Mark solution as invalid
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.solutions![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionElementInvalid).toBe(true);
    });

    it('should detect SA spot invalid change', () => {
        comp.ngOnInit();

        // Mark spot as invalid
        const saQuestion = comp.quizExercise.quizQuestions![2] as ShortAnswerQuestion;
        saQuestion.spots![0].invalid = true;
        comp.loadQuizSuccess();

        expect(comp.questionElementInvalid).toBe(true);
    });

    it('should confirm change successfully', () => {
        vi.spyOn(quizReEvaluateService, 'reevaluate').mockReturnValue(of(new HttpResponse({ body: createQuizExercise() })));
        comp.files = new Map();

        comp.confirmChange();

        expect(comp.busy).toBe(false);
        expect(comp.successful).toBe(true);
        expect(comp.failed).toBe(false);
    });

    it('should handle confirm change error', () => {
        vi.spyOn(quizReEvaluateService, 'reevaluate').mockReturnValue(throwError(() => new Error('error')));
        comp.files = new Map();

        comp.confirmChange();

        expect(comp.busy).toBe(false);
        expect(comp.successful).toBe(false);
        expect(comp.failed).toBe(true);
    });

    it('should close and navigate', () => {
        vi.useFakeTimers();
        const closeSpy = vi.spyOn(activeModal, 'close');
        const navigateSpy = vi.spyOn(navigationUtilService, 'navigateBackFromExerciseUpdate').mockImplementation(() => {});

        comp.closeAndNavigate();

        expect(closeSpy).toHaveBeenCalledOnce();

        vi.runAllTimers();
        expect(navigateSpy).toHaveBeenCalledWith(comp.quizExercise);

        vi.useRealTimers();
    });

    it('should have icons defined', () => {
        expect(comp.faBan).toBeDefined();
        expect(comp.faSpinner).toBeDefined();
        expect(comp.faTimes).toBeDefined();
        expect(comp.faCheck).toBeDefined();
        expect(comp.faCheckCircle).toBeDefined();
    });
});
