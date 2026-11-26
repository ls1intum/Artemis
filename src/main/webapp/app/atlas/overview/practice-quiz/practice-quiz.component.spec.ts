import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { ActivatedRoute, ParamMap, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { PracticeQuizComponent } from 'app/atlas/overview/practice-quiz/practice-quiz.component';
import { PracticeQuizService } from 'app/atlas/overview/practice-quiz/practice-quiz.service';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { PracticeSessionDTO, PracticeSessionReviewDTO, PracticeSessionSummaryDTO } from 'app/atlas/overview/practice-quiz/practice-quiz.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('PracticeQuizComponent', () => {
    let fixture: ComponentFixture<PracticeQuizComponent>;
    let component: PracticeQuizComponent;
    let practiceQuizService: PracticeQuizService;
    let paramMap: BehaviorSubject<ParamMap>;

    const mockSession: PracticeSessionDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        questions: [
            {
                id: 101,
                title: 'Question 1',
                text: 'Polymorphism',
                points: 2,
                type: 'multiple-choice',
                singleChoice: false,
                answerOptions: [
                    { id: 201, text: 'A' },
                    { id: 202, text: 'B' },
                ],
            },
        ],
    };

    const mockReview: PracticeSessionReviewDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        completedAt: '2026-11-17T12:05:00Z',
        score: 2,
        maxPoints: 2,
        questions: [
            {
                question: {
                    id: 101,
                    title: 'Question 1',
                    text: 'Polymorphism',
                    points: 2,
                    type: 'multiple-choice',
                    answerOptions: [
                        { id: 201, text: 'A', isCorrect: true, explanation: 'Correct' },
                        { id: 202, text: 'B', isCorrect: false },
                    ],
                },
                isAdaptive: true,
                score: 2,
                submittedAnswer: '{"selectedOptions":[{"id":201}]}',
            },
        ],
    };

    const mockSummary: PracticeSessionSummaryDTO = {
        id: 42,
        courseId: 1,
        startTime: '2026-11-17T12:00:00Z',
        completedAt: '2026-11-17T12:05:00Z',
        score: 2,
        maxPoints: 2,
        completed: true,
    };

    beforeEach(async () => {
        paramMap = new BehaviorSubject(convertToParamMap({ courseId: '1' }));
        await TestBed.configureTestingModule({
            imports: [PracticeQuizComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe), MockComponent(MultipleChoiceQuestionComponent)],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(PracticeQuizService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            paramMap,
                        },
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PracticeQuizComponent);
        component = fixture.componentInstance;
        practiceQuizService = TestBed.inject(PracticeQuizService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should stay in OVERVIEW when no active session exists', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([mockSummary]));

        component.ngOnInit();

        expect(component.state()).toBe('OVERVIEW');
        expect(component.pastSessions()).toEqual([mockSummary]);
        expect(component.currentSession()).toBeNull();
    });

    it('should resume into QUIZ state when an active session is returned', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(mockSession));

        component.ngOnInit();

        expect(component.state()).toBe('QUIZ');
        expect(component.currentSession()).toEqual(mockSession);
        expect(component.activeQuestions()).toHaveLength(1);
        expect(component.activeQuestions()[0].id).toBe(101);
    });

    it('should start a new quiz session on startNewQuiz', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        vi.spyOn(practiceQuizService, 'startSession').mockReturnValue(of(mockSession));

        component.ngOnInit();
        component.startNewQuiz();

        expect(component.state()).toBe('QUIZ');
        expect(component.currentSession()).toEqual(mockSession);
        expect(component.activeQuestions()).toHaveLength(1);
        expect(component.activeQuestions()[0].id).toBe(101);
    });

    it('should update selected options on onSelectedAnswerOptionsChange and report isAnswered', () => {
        const optionA = { id: 201, text: 'Option A' } as AnswerOption;
        const optionB = { id: 202, text: 'Option B' } as AnswerOption;

        expect(component.isAnswered(101)).toBe(false);

        component.onSelectedAnswerOptionsChange(101, [optionA]);
        expect(component.selectedAnswerOptions()[101]).toEqual([optionA]);
        expect(component.isAnswered(101)).toBe(true);

        component.onSelectedAnswerOptionsChange(101, [optionA, optionB]);
        expect(component.selectedAnswerOptions()[101]).toEqual([optionA, optionB]);

        // Zero-based option IDs across multiple questions
        const optionZero = { id: 0, text: 'Option 0' } as AnswerOption;
        component.onSelectedAnswerOptionsChange(101, [optionZero]);
        expect(component.isAnswered(101)).toBe(true);
        expect(component.selectedAnswerOptions()[101]).toEqual([optionZero]);

        const optionQ2 = { id: 0, text: 'Q2 Option 0' } as AnswerOption;
        component.onSelectedAnswerOptionsChange(102, [optionQ2]);
        expect(component.selectedAnswerOptions()[101]).toEqual([optionZero]);
        expect(component.selectedAnswerOptions()[102]).toEqual([optionQ2]);
    });

    it('should generate question tooltip in review mode and support navigateToQuestion', () => {
        component.setupReview(mockReview);

        expect(component.questionTooltip(101, 2)).toBe('2/2 P');
        expect(component.reviewQuestions()[0].scoringType).toBe(ScoringType.ALL_OR_NOTHING);
        expect(component.reviewSubmittedResult()).toBeDefined();
        const submission = component.reviewSubmittedResult()?.submission;
        expect(submission).toBeInstanceOf(QuizSubmission);
        expect(submission instanceof QuizSubmission ? submission.submittedAnswers : undefined).toHaveLength(1);

        const scrollSpy = vi.fn();
        const mockEl = { scrollIntoView: scrollSpy } as unknown as HTMLElement;
        vi.spyOn(document, 'getElementById').mockReturnValue(mockEl);

        component.navigateToQuestion(0);
        expect(scrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
    });

    it('should submit answers and transition to REVIEW state', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        component.ngOnInit();

        component.setupQuizSession(mockSession);
        const optionA = { id: 201, text: 'Option A' } as AnswerOption;
        component.onSelectedAnswerOptionsChange(101, [optionA]);

        const submitSpy = vi.spyOn(practiceQuizService, 'submitSession').mockReturnValue(of(mockReview));

        component.submitQuiz();

        expect(submitSpy).toHaveBeenCalledWith(1, 42, {
            answers: [
                {
                    type: 'multiple-choice',
                    quizQuestion: { id: 101 },
                    selectedOptions: [{ id: 201 }],
                },
            ],
        });
        expect(component.state()).toBe('REVIEW');
        expect(component.reviewSession()).toEqual(mockReview);
        expect(component.reviewQuestions()).toHaveLength(1);
        expect(component.reviewSelectedAnswerOptions()[101]).toHaveLength(1);
        expect(component.reviewSelectedAnswerOptions()[101][0].id).toBe(201);
        expect(component.questionScores()[101]).toBe(2);
        expect(component.currentSession()).toBeNull();
    });

    it('should open review of past session and return back to overview', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([mockSummary]));
        vi.spyOn(practiceQuizService, 'getSessionReview').mockReturnValue(of(mockReview));

        component.ngOnInit();
        component.openReview(42);

        expect(component.state()).toBe('REVIEW');
        expect(component.reviewSession()).toEqual(mockReview);
        expect(component.reviewQuestions()).toHaveLength(1);
        expect(component.questionScores()[101]).toBe(2);

        component.backToOverview();
        expect(component.state()).toBe('OVERVIEW');
        expect(component.reviewSession()).toBeNull();
        expect(component.reviewQuestions()).toEqual([]);
    });

    it('should reset the previous course quiz when the route switches to another course', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockImplementation((courseId: number) => of(courseId === 1 ? mockSession : null));
        const pastSpy = vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));

        component.ngOnInit();
        component.onSelectedAnswerOptionsChange(101, [{ id: 201 } as AnswerOption]);
        expect(component.state()).toBe('QUIZ');

        paramMap.next(convertToParamMap({ courseId: '2' }));

        expect(component.activeCourseId()).toBe(2);
        expect(component.state()).toBe('OVERVIEW');
        expect(component.currentSession()).toBeNull();
        expect(component.activeQuestions()).toEqual([]);
        expect(component.selectedAnswerOptions()).toEqual({});
        expect(pastSpy).toHaveBeenCalledWith(2);
    });

    it('should resume the session of the selected row instead of the latest one', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([mockSummary]));
        const resumeSpy = vi.spyOn(practiceQuizService, 'resumeSession').mockReturnValue(of(mockSession));

        component.ngOnInit();
        component.onSelectedAnswerOptionsChange(101, [{ id: 201 } as AnswerOption]);
        component.resumeSession(42);

        expect(resumeSpy).toHaveBeenCalledWith(1, 42);
        expect(component.state()).toBe('QUIZ');
        expect(component.currentSession()).toEqual(mockSession);
        expect(component.selectedAnswerOptions()).toEqual({});
    });

    it('should label each question navigation button with its number and block cancelling while submitting', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(mockSession));
        fixture.detectChanges();
        component.isSubmitting.set(true);
        fixture.detectChanges();

        const navButtons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('.quiz-nav-step'));
        expect(navButtons.map((button) => button.getAttribute('aria-label'))).toEqual(['Question 1']);
        const cancelButton = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('#quiz-header button')).find((button) => button.textContent?.trim() === 'Cancel');
        expect(cancelButton?.disabled).toBe(true);
    });

    it('should cancel pending requests of the previous course when the route switches to another course', () => {
        const pendingStart = new Subject<PracticeSessionDTO>();
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        vi.spyOn(practiceQuizService, 'startSession').mockReturnValue(pendingStart);

        component.ngOnInit();
        component.startNewQuiz();
        expect(component.isLoading()).toBe(true);
        expect(pendingStart.observed).toBe(true);

        paramMap.next(convertToParamMap({ courseId: '2' }));

        expect(pendingStart.observed).toBe(false);
        pendingStart.next(mockSession);
        expect(component.activeCourseId()).toBe(2);
        expect(component.state()).toBe('OVERVIEW');
        expect(component.currentSession()).toBeNull();
        expect(component.isLoading()).toBe(false);
    });

    it('should show the readable server error instead of its translation key when generation fails', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(of(null));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(of([]));
        vi.spyOn(practiceQuizService, 'startSession').mockReturnValue(
            throwError(() => ({ error: { title: 'Quiz question pool service is not available', message: 'error.poolNotAvailable' } })),
        );

        component.ngOnInit();
        component.startNewQuiz();

        expect(component.state()).toBe('OVERVIEW');
        expect(component.isLoading()).toBe(false);
        expect(component.errorMessage()).toBe('Quiz question pool service is not available');
    });

    it('should fall back to a default message when an error has no title', () => {
        vi.spyOn(practiceQuizService, 'getCurrentSession').mockReturnValue(throwError(() => ({})));
        vi.spyOn(practiceQuizService, 'getPastSessions').mockReturnValue(throwError(() => ({ error: { message: 'error.http.500' } })));

        component.ngOnInit();
        expect(component.errorMessage()).toBe('Failed to load past sessions.');

        component.setupQuizSession(mockSession);
        vi.spyOn(practiceQuizService, 'submitSession').mockReturnValue(throwError(() => ({})));
        component.submitQuiz();
        expect(component.isSubmitting()).toBe(false);
        expect(component.state()).toBe('QUIZ');
        expect(component.errorMessage()).toBe('Failed to submit quiz.');

        vi.spyOn(practiceQuizService, 'getSessionReview').mockReturnValue(throwError(() => ({})));
        component.openReview(42);
        expect(component.isLoading()).toBe(false);
        expect(component.state()).toBe('QUIZ');
    });

    it('should shuffle answer options through the quiz service when a quiz starts', () => {
        const randomizeSpy = vi.spyOn(TestBed.inject(ArtemisQuizService), 'randomizeOrder');

        component.setupQuizSession(mockSession);

        expect(randomizeSpy).toHaveBeenCalledWith(component.activeQuestions(), false);
        expect(component.activeQuestions()[0].randomizeOrder).toBe(true);
        expect(
            component
                .activeQuestions()[0]
                .answerOptions?.map((option) => option.id)
                .sort(),
        ).toEqual([201, 202]);
    });
});
