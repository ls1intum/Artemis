import { type MockInstance, beforeEach, describe, expect, it, vi, afterEach as vitestAfterEach } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizParticipationComponent } from 'app/quiz/overview/participation/quiz-participation.component';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockWebsocketService } from 'src/test/javascript/spec/helpers/mocks/service/mock-websocket.service';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizParticipationService } from 'app/quiz/overview/service/quiz-participation.service';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { MockRouter } from 'src/test/javascript/spec/helpers/mocks/mock-router';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { ShortAnswerQuestionComponent } from '../../shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from '../../shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from '../../shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestion } from '../../shared/entities/short-answer-question.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { captureException } from '@sentry/angular';
import * as QuizStepWizardUtil from 'app/quiz/shared/questions/quiz-stepwizard.util';

vi.mock('@sentry/angular', () => ({
    captureException: vi.fn(),
}));

const now = dayjs();
const question1: QuizQuestion = {
    id: 1,
    type: QuizQuestionType.DRAG_AND_DROP,
    points: 1,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question2: MultipleChoiceQuestion = {
    id: 2,
    type: QuizQuestionType.MULTIPLE_CHOICE,
    points: 2,
    answerOptions: [{ id: 1 } as AnswerOption],
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
};
const question3: ShortAnswerQuestion = {
    id: 3,
    type: QuizQuestionType.SHORT_ANSWER,
    points: 3,
    invalid: false,
    exportQuiz: false,
    randomizeOrder: true,
    text: 'Short answer question text',
    matchLetterCase: false,
    similarityValue: 0,
    spots: [],
};

const createQuizExercise = (): QuizExercise => ({
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(2, 'minutes'),
    duration: 60 * 4,
    dueDate: dayjs(now).add(2, 'minutes'),
    quizStarted: true,
    quizBatches: [
        {
            startTime: dayjs(now).subtract(2, 'minutes'),
            started: true,
        },
    ],
    quizMode: QuizMode.SYNCHRONIZED,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
});

const quizExerciseForPractice: QuizExercise = {
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(4, 'minutes'),
    dueDate: dayjs(now).subtract(2, 'minutes'),
    quizStarted: true,
    quizEnded: true,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

const quizExerciseForResults: QuizExercise = {
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(4, 'minutes'),
    dueDate: dayjs(now).subtract(2, 'minutes'),
    duration: 60 * 2,
    quizStarted: true,
    quizEnded: true,
    quizBatches: [
        {
            startTime: dayjs(now).subtract(4, 'minutes'),
            started: true,
        },
    ],
    quizMode: QuizMode.SYNCHRONIZED,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

const quizExerciseUnreleased: QuizExercise = {
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).add(2, 'days'),
    dueDate: dayjs(now).add(4, 'days'),
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

// Single setupTestBed for all tests in this file
setupTestBed({ zoneless: true });

describe('QuizParticipationComponent - live mode', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let participationSpy: MockInstance;
    let httpMock: HttpTestingController;
    let participationService: ParticipationService;
    let quizExerciseService: QuizExerciseService;
    let quizExercise: QuizExercise;

    beforeEach(async () => {
        TestBed.resetTestingModule();
        quizExercise = createQuizExercise();

        await TestBed.configureTestingModule({
            imports: [
                QuizParticipationComponent,
                MockComponent(FaIconComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                QuizExerciseService,
                QuizParticipationService,
                ArtemisQuizService,
                SubmissionService,
                AlertService,
                ArtemisServerDateService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ exerciseId: 1 }),
                        data: of({ mode: 'live' }),
                        parent: { parent: { params: of({ courseId: 1 }) } },
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizParticipationComponent);
        component = fixture.componentInstance;

        participationService = TestBed.inject(ParticipationService);
        const participation: StudentParticipation = { exercise: { ...quizExercise } };
        participationSpy = vi.spyOn(participationService, 'startQuizParticipation').mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));
        quizExerciseService = TestBed.inject(QuizExerciseService);
        vi.spyOn(quizExerciseService, 'findForStudent').mockReturnValue(of({ body: { ...quizExercise } } as HttpResponse<QuizExercise>));
        httpMock = TestBed.inject(HttpTestingController);
    });

    vitestAfterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
    });

    it('should capture exception when element is not found', () => {
        const questionIndex = 1;
        vi.spyOn(document, 'getElementById').mockReturnValue(null);

        component.navigateToQuestion(questionIndex);

        expect(captureException).toHaveBeenCalledWith('navigateToQuestion: element not found for index ' + questionIndex);
    });

    it('should highlight the correct quiz question', () => {
        const addTemporaryHighlightToQuestionSpy = vi.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        const mockQuestion: QuizQuestion = {
            id: 1,
            type: QuizQuestionType.MULTIPLE_CHOICE,
            points: 1,
            randomizeOrder: false,
            invalid: false,
            exportQuiz: false,
        };
        component.quizExercise = { ...quizExercise, quizQuestions: [mockQuestion] };

        component['highlightQuestion'](0);

        expect(addTemporaryHighlightToQuestionSpy).toHaveBeenCalledWith(mockQuestion);
    });

    it('should not highlight if question is not found', () => {
        const addTemporaryHighlightToQuestionSpy = vi.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        component.quizExercise = { ...quizExercise, quizQuestions: [] };

        component['highlightQuestion'](1);

        expect(addTemporaryHighlightToQuestionSpy).not.toHaveBeenCalled();
    });

    it('should not highlight if quizQuestions is undefined', () => {
        const addTemporaryHighlightToQuestionSpy = vi.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
        component.quizExercise = { ...quizExercise, quizQuestions: undefined };

        component['highlightQuestion'](1);

        expect(addTemporaryHighlightToQuestionSpy).not.toHaveBeenCalled();
    });

    it('should fetch exercise and create a new submission', () => {
        fixture.detectChanges();

        expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
        expect(component.quizExercise).toEqual(quizExercise);
        expect(component.waitingForQuizStart).toBeFalse();
        expect(component.totalScore).toBe(6);
        expect(component.dragAndDropMappings.get(question1.id!)).toEqual([]);
        expect(component.selectedAnswerOptions.get(question2.id!)).toEqual([]);
        expect(component.shortAnswerSubmittedTexts.get(question3.id!)).toEqual([]);
        expect(component.submission).not.toBeNull();
    });

    it('should update in intervals of individual quiz', () => {
        vi.useFakeTimers();
        const individualQuizExercise = { ...quizExercise };
        individualQuizExercise.quizMode = QuizMode.INDIVIDUAL;
        individualQuizExercise.quizStarted = false;
        individualQuizExercise.quizBatches = [
            {
                startTime: dayjs(now).subtract(2, 'minutes'),
                started: false,
            },
        ];
        participationSpy = vi
            .spyOn(participationService, 'startQuizParticipation')
            .mockReturnValue(of({ body: { exercise: individualQuizExercise } } as HttpResponse<StudentParticipation>));
        fixture.detectChanges();

        const updateSpy = vi.spyOn(component, 'updateDisplayedTimes');
        const refreshSpy = vi.spyOn(component, 'refreshQuiz').mockImplementation(() => {});
        vi.advanceTimersByTime(5000);
        fixture.detectChanges();

        expect(updateSpy).toHaveBeenCalledTimes(50);
        expect(refreshSpy).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should update in intervals of not individual quiz', () => {
        vi.useFakeTimers();
        const notIndividualQuizExercise = { ...quizExercise };
        notIndividualQuizExercise.quizMode = QuizMode.SYNCHRONIZED;
        notIndividualQuizExercise.quizStarted = false;
        notIndividualQuizExercise.quizBatches = [
            {
                startTime: dayjs(now).subtract(2, 'minutes'),
                started: false,
            },
        ];
        participationSpy = vi
            .spyOn(participationService, 'startQuizParticipation')
            .mockReturnValue(of({ body: { exercise: notIndividualQuizExercise } } as HttpResponse<StudentParticipation>));
        fixture.detectChanges();

        const updateSpy = vi.spyOn(component, 'updateDisplayedTimes');
        const refreshSpy = vi.spyOn(component, 'refreshQuiz').mockImplementation(() => {});
        vi.advanceTimersByTime(5000);
        fixture.detectChanges();

        expect(updateSpy).toHaveBeenCalledTimes(50);
        expect(refreshSpy).toHaveBeenCalledTimes(0);

        vi.useRealTimers();
    });

    it('should check quiz end in intervals', () => {
        vi.useFakeTimers();
        fixture.detectChanges();

        const checkQuizEndSpy = vi.spyOn(component, 'checkForQuizEnd');
        vi.advanceTimersByTime(5000);
        fixture.detectChanges();

        expect(checkQuizEndSpy).toHaveBeenCalledTimes(50);
        vi.useRealTimers();
    });

    it('should trigger a save on quiz end if the answers were not submitted', () => {
        vi.useFakeTimers();
        fixture.detectChanges();

        component.endDate = dayjs().add(1, 'seconds');
        component.quizExercise.quizMode = QuizMode.BATCHED;
        component.submission.submissionDate = dayjs();
        component.submission.submitted = false;

        const triggerSaveStub = vi.spyOn(component, 'triggerSave').mockImplementation(() => {});
        const checkQuizEndSpy = vi.spyOn(component, 'checkForQuizEnd');

        vi.advanceTimersByTime(2000);
        fixture.detectChanges();

        expect(checkQuizEndSpy).toHaveBeenCalledTimes(20);
        expect(triggerSaveStub).toHaveBeenCalledOnce();
        vi.useRealTimers();
    });

    it('should refresh quiz', () => {
        const exerciseService = TestBed.inject(QuizExerciseService);
        fixture.detectChanges();

        component.quizExercise.quizStarted = false;
        component.quizBatch!.started = false;
        component.quizBatch!.startTime = undefined;

        vi.spyOn(exerciseService, 'findForStudent').mockReturnValue(
            of({
                body: {
                    ...quizExercise,
                    quizEnded: true,
                },
            } as HttpResponse<QuizExercise>),
        );
        fixture.detectChanges();

        vi.spyOn(component, 'initLiveMode');

        component.refreshQuiz();

        expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
    });

    it('should return true if student didnt interact with any question', () => {
        component.quizExercise = { ...quizExercise, quizQuestions: undefined };
        expect(component.areAllQuestionsAnswered()).toBeTrue();

        component.quizExercise = { ...quizExercise, quizQuestions: [] };
        expect(component.areAllQuestionsAnswered()).toBeTrue();

        component.quizExercise = quizExercise;
        component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        component.selectedAnswerOptions.set(2, []);
        expect(component.areAllQuestionsAnswered()).toBeFalse();

        component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        component.dragAndDropMappings.set(1, []);
        expect(component.areAllQuestionsAnswered()).toBeFalse();

        component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        component.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
        component.shortAnswerSubmittedTexts.set(3, []);
        expect(component.areAllQuestionsAnswered()).toBeFalse();
    });

    it('should show results after ending', () => {
        fixture.detectChanges();

        const answer: SubmittedAnswer = { scoreInPoints: 1, quizQuestion: question2 };
        const quizSubmission: QuizSubmission = {
            submissionDate: now.subtract(3, 'minutes'),
            submitted: true,
            submittedAnswers: [answer],
        };
        const result: Result = {
            submission: quizSubmission,
        };
        component.result = result;
        component.submission = quizSubmission;
        component.quizExercise = quizExerciseForResults;
        component.showingResult = true;

        expect(component.showingResult).toBeTrue();
    });

    it('should mark changes as unsaved when an answer changes', () => {
        fixture.detectChanges();
        component.unsavedChanges = false;
        component.onSelectionChanged();
        expect(component.unsavedChanges).toBeTrue();
    });

    it('should react to errors', () => {
        fixture.detectChanges();

        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'addAlert');

        component.onSubmitError({ message: 'error' } as any);
        expect(errorSpy).toHaveBeenCalled();
    });

    it('should express timespan in humanized text', () => {
        fixture.detectChanges();

        component.remainingTimeSeconds = 90;
        const result = component.relativeTimeText();
        expect(result).toBeDefined();
    });

    it('should adjust release date of the quiz if it didnt start', () => {
        fixture.detectChanges();

        component.quizExercise = quizExerciseUnreleased;
        component.quizExercise.quizStarted = false;

        component.updateDisplayedTimes();

        expect(component.waitingForQuizStart).toBeTrue();
    });

    it('should apply participation', () => {
        fixture.detectChanges();

        const participation: StudentParticipation = {
            exercise: quizExercise,
            results: [{ submission: { submittedAnswers: [] } as QuizSubmission }],
        };

        component.applyParticipation(participation);

        expect(component.quizExercise).toEqual(quizExercise);
    });
});

describe('QuizParticipationComponent - preview mode', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let httpMock: HttpTestingController;
    let exerciseService: QuizExerciseService;
    let quizExercise: QuizExercise;

    beforeEach(async () => {
        TestBed.resetTestingModule();
        quizExercise = createQuizExercise();

        await TestBed.configureTestingModule({
            imports: [
                QuizParticipationComponent,
                MockComponent(FaIconComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                QuizExerciseService,
                QuizParticipationService,
                ArtemisQuizService,
                SubmissionService,
                AlertService,
                ArtemisServerDateService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ exerciseId: 1 }),
                        data: of({ mode: 'preview' }),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizParticipationComponent);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(QuizExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    vitestAfterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const serviceStub = vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
        fixture.detectChanges();
        expect(serviceStub).toHaveBeenCalledWith(quizExercise.id);
    });

    it('should initialize and start', () => {
        const quizService = TestBed.inject(ArtemisQuizService);
        const serviceSpy = vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
        const startSpy = vi.spyOn(component, 'startQuizPreviewOrPractice');
        const randomizeSpy = vi.spyOn(quizService, 'randomizeOrder');
        fixture.detectChanges();

        expect(serviceSpy).toHaveBeenCalledWith(quizExercise.id);
        expect(startSpy).toHaveBeenCalledOnce();
        expect(randomizeSpy).toHaveBeenCalledOnce();
    });

    it('should submit quiz', () => {
        vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
        fixture.detectChanges();

        component.onSubmit();

        expect(component.showingResult).toBeTrue();
    });
});

describe('QuizParticipationComponent - practice mode', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let httpMock: HttpTestingController;
    let exerciseService: QuizExerciseService;

    beforeEach(async () => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [
                QuizParticipationComponent,
                MockComponent(FaIconComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                QuizExerciseService,
                QuizParticipationService,
                ArtemisQuizService,
                SubmissionService,
                AlertService,
                ArtemisServerDateService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: 1, exerciseId: 1 }),
                        data: of({ mode: 'practice' }),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizParticipationComponent);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(QuizExerciseService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    vitestAfterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        const serviceSpy = vi.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
        fixture.detectChanges();

        expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
    });

    it('should initialize and start', () => {
        const quizService = TestBed.inject(ArtemisQuizService);
        const serviceSpy = vi.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
        const startSpy = vi.spyOn(component, 'startQuizPreviewOrPractice');
        const randomizeSpy = vi.spyOn(quizService, 'randomizeOrder');
        fixture.detectChanges();

        expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
        expect(startSpy).toHaveBeenCalledOnce();
        expect(randomizeSpy).toHaveBeenCalledOnce();
    });

    it('should submit quiz', () => {
        const serviceSpy = vi.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
        fixture.detectChanges();

        component.onSubmit();
        fixture.detectChanges();

        const request = httpMock.expectOne({ method: 'POST' });
        request.flush({
            submissionDate: now,
            submitted: true,
            results: [{ score: 100 }],
        } as Result);
        expect(request.request.url).toBe(`api/quiz/exercises/${quizExerciseForPractice.id}/submissions/practice`);
        fixture.detectChanges();

        expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
    });
});

describe('QuizParticipationComponent - solution mode', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let exerciseService: QuizExerciseService;
    let resultForSolutionServiceSpy: MockInstance;

    beforeEach(async () => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [
                QuizParticipationComponent,
                MockComponent(FaIconComponent),
                MockComponent(MultipleChoiceQuestionComponent),
                MockComponent(DragAndDropQuestionComponent),
                MockComponent(ShortAnswerQuestionComponent),
                MockComponent(ButtonComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                QuizExerciseService,
                QuizParticipationService,
                ArtemisQuizService,
                SubmissionService,
                AlertService,
                ArtemisServerDateService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LocalStorageService),
                MockProvider(SessionStorageService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: 1, exerciseId: 1 }),
                        data: of({ mode: 'solution' }),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizParticipationComponent);
        component = fixture.componentInstance;

        exerciseService = TestBed.inject(QuizExerciseService);
        resultForSolutionServiceSpy = vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
    });

    vitestAfterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
    });

    it('should initialize and show solution', () => {
        fixture.detectChanges();

        expect(resultForSolutionServiceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
        expect(component.showingResult).toBeTrue();
        expect(component.totalScore).toBe(6);
    });

    it('should update time', () => {
        fixture.detectChanges();

        component.remainingTimeSeconds = 100;
        component.updateDisplayedTimes();

        // In solution mode, we're just showing results
        expect(component.showingResult).toBeTrue();
    });
});
