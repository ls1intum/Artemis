import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
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
import { MockBuilder } from 'ng-mocks';
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

jest.mock('@sentry/angular', () => ({
    captureException: jest.fn(),
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

let quizExercise: QuizExercise;

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

describe('QuizParticipationComponent', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let participationSpy: jest.SpyInstance;
    let resultForSolutionServiceSpy: jest.SpyInstance;
    let httpMock: HttpTestingController;
    let exerciseService: QuizExerciseService;
    let participationService: ParticipationService;
    let quizExerciseService: QuizExerciseService;

    beforeEach(() => {
        quizExercise = {
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
        };
    });

    describe('live mode', () => {
        beforeEach(() => {
            MockBuilder(QuizParticipationComponent)
                .keep(FaIconComponent)
                .keep(MultipleChoiceQuestionComponent)
                .keep(DragAndDropQuestionComponent)
                .keep(ShortAnswerQuestionComponent)
                .keep(QuizExerciseService)
                .keep(ButtonComponent)
                .keep(QuizParticipationService)
                .keep(ArtemisQuizService)
                .keep(SubmissionService)
                .keep(AlertService)
                .keep(ArtemisServerDateService)
                .keep(Router)
                .provide(provideHttpClient())
                .provide(provideHttpClientTesting())
                .provide({ provide: TranslateService, useClass: MockTranslateService })
                .provide(LocalStorageService)
                .provide(SessionStorageService)
                .provide({ provide: WebsocketService, useClass: MockWebsocketService })
                .provide({
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ exerciseId: quizExercise.id }),
                        data: of({ mode: 'live' }),
                        parent: { parent: { params: of({ courseId: 1 }) } },
                    },
                })
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    participationService = fixture.debugElement.injector.get(ParticipationService);
                    const participation: StudentParticipation = { exercise: { ...quizExercise } };
                    participationSpy = jest
                        .spyOn(participationService, 'startQuizParticipation')
                        .mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));
                    quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
                    jest.spyOn(quizExerciseService, 'findForStudent').mockReturnValue(of({ body: { ...quizExercise } } as HttpResponse<QuizExercise>));
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(() => {
            httpMock.verify();
            jest.restoreAllMocks();
        });

        afterEach(fakeAsync(() => {
            discardPeriodicTasks();
        }));

        it('should initialize', () => {
            fixture.changeDetectorRef.detectChanges();
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
        });

        it('should capture exception when element is not found', () => {
            const questionIndex = 1;
            jest.spyOn(document, 'getElementById').mockReturnValue(null);

            component.navigateToQuestion(questionIndex);

            expect(captureException).toHaveBeenCalledWith('navigateToQuestion: element not found for index ' + questionIndex);
        });

        it('should highlight the correct quiz question', () => {
            const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
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
            const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
            component.quizExercise = { ...quizExercise, quizQuestions: [] };

            component['highlightQuestion'](1);

            expect(addTemporaryHighlightToQuestionSpy).not.toHaveBeenCalled();
        });

        it('should not highlight if quizQuestions is undefined', () => {
            const addTemporaryHighlightToQuestionSpy = jest.spyOn(QuizStepWizardUtil, 'addTemporaryHighlightToQuestion');
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

        it('should update in intervals of individual quiz', fakeAsync(() => {
            const individualQuizExercise = { ...quizExercise };
            individualQuizExercise.quizMode = QuizMode.INDIVIDUAL;
            individualQuizExercise.quizStarted = false;
            individualQuizExercise.quizBatches = [
                {
                    startTime: dayjs(now).subtract(2, 'minutes'),
                    started: false,
                },
            ];
            participationSpy = jest
                .spyOn(participationService, 'startQuizParticipation')
                .mockReturnValue(of({ body: { exercise: individualQuizExercise } } as HttpResponse<StudentParticipation>));
            fixture.detectChanges();

            const updateSpy = jest.spyOn(component, 'updateDisplayedTimes');
            const refreshSpy = jest.spyOn(component, 'refreshQuiz').mockImplementation();
            tick(5000);
            fixture.changeDetectorRef.detectChanges();
            discardPeriodicTasks();

            expect(updateSpy).toHaveBeenCalledTimes(50);
            expect(refreshSpy).toHaveBeenCalledOnce();
        }));

        it('should update in intervals of not individual quiz', fakeAsync(() => {
            const notIndividualQuizExercise = { ...quizExercise };
            notIndividualQuizExercise.quizMode = QuizMode.SYNCHRONIZED;
            notIndividualQuizExercise.quizStarted = false;
            notIndividualQuizExercise.quizBatches = [
                {
                    startTime: dayjs(now).subtract(2, 'minutes'),
                    started: false,
                },
            ];
            participationSpy = jest
                .spyOn(participationService, 'startQuizParticipation')
                .mockReturnValue(of({ body: { exercise: notIndividualQuizExercise } } as HttpResponse<StudentParticipation>));
            fixture.detectChanges();

            const updateSpy = jest.spyOn(component, 'updateDisplayedTimes');
            const refreshSpy = jest.spyOn(component, 'refreshQuiz').mockImplementation();
            tick(5000);
            fixture.changeDetectorRef.detectChanges();
            discardPeriodicTasks();

            expect(updateSpy).toHaveBeenCalledTimes(50);
            expect(refreshSpy).toHaveBeenCalledTimes(0);

            tick(5000);
        }));

        it('should check quiz end in intervals', fakeAsync(() => {
            fixture.detectChanges();

            const checkQuizEndSpy = jest.spyOn(component, 'checkForQuizEnd');
            tick(5000);
            fixture.changeDetectorRef.detectChanges();
            discardPeriodicTasks();

            expect(checkQuizEndSpy).toHaveBeenCalledTimes(50);
        }));

        it('should trigger a save on quiz end if the answers were not submitted', fakeAsync(() => {
            fixture.detectChanges();

            component.endDate = dayjs().add(1, 'seconds');
            component.quizExercise.quizMode = QuizMode.BATCHED;
            component.submission.submissionDate = dayjs();
            component.submission.submitted = false;

            const triggerSaveStub = jest.spyOn(component, 'triggerSave').mockImplementation();
            const checkQuizEndSpy = jest.spyOn(component, 'checkForQuizEnd');

            tick(2000);
            fixture.changeDetectorRef.detectChanges();
            discardPeriodicTasks();

            expect(checkQuizEndSpy).toHaveBeenCalledTimes(20);
            expect(triggerSaveStub).toHaveBeenCalledOnce();
        }));

        it('should refresh quiz', () => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            fixture.detectChanges();

            component.quizExercise.quizStarted = false;
            component.quizBatch!.started = false;
            component.quizBatch!.startTime = undefined;

            // Returns the started exercise
            const findStudentSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(
                of({
                    body: {
                        ...quizExercise,
                        quizEnded: true,
                    },
                } as HttpResponse<QuizExercise>),
            );
            fixture.changeDetectorRef.detectChanges();

            const initLiveModeSpy = jest.spyOn(component, 'initLiveMode');

            const refreshButton = fixture.debugElement.nativeElement.querySelector('#refresh-quiz button');
            expect(refreshButton).not.toBeNull();

            refreshButton.click();
            fixture.detectChanges();

            expect(initLiveModeSpy).toHaveBeenCalledOnce();
            expect(findStudentSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
        });

        it.each([
            [QuizMode.BATCHED, false],
            [QuizMode.BATCHED, true],
            [QuizMode.INDIVIDUAL, false],
            [QuizMode.INDIVIDUAL, true],
        ])('should join %s batches that have started %p', (quizMode: QuizMode, started: boolean) => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            const participationService = fixture.debugElement.injector.get(ParticipationService);
            const participation: StudentParticipation = {
                exercise: {
                    ...quizExercise,
                    quizBatches: [],
                    quizMode,
                    quizStarted: false,
                } as QuizExercise,
            };
            participationSpy = jest.spyOn(participationService, 'startQuizParticipation').mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));

            fixture.detectChanges();

            // Returns the started exercise
            const joinBatchSpy = jest.spyOn(exerciseService, 'join').mockReturnValue(of({ body: { started } } as HttpResponse<QuizBatch>));
            fixture.detectChanges();

            const refreshQuizSpy = jest.spyOn(component, 'refreshQuiz').mockReturnValue();

            const joinButton = fixture.debugElement.nativeElement.querySelector(quizMode === QuizMode.BATCHED ? '#join-batch button' : '#start-batch button');
            expect(joinButton).not.toBeNull();

            joinButton.click();
            fixture.detectChanges();

            expect(refreshQuizSpy).toHaveBeenCalledTimes(started ? 1 : 0);
            expect(joinBatchSpy).toHaveBeenCalledWith(quizExercise.id, '');
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
        });

        it('should submit quiz', () => {
            const individualQuizExercise = { ...quizExercise };
            individualQuizExercise.quizMode = QuizMode.INDIVIDUAL;
            participationSpy = jest
                .spyOn(participationService, 'startQuizParticipation')
                .mockReturnValue(of({ body: { exercise: individualQuizExercise } } as HttpResponse<StudentParticipation>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBeNull();

            submitButton.click();
            fixture.changeDetectorRef.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/quiz/exercises/${quizExercise.id}/submissions/live`);
            fixture.changeDetectorRef.detectChanges();

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBeFalse();
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

        it('should show warning on submit', () => {
            const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
            fixture.detectChanges();

            // Set a value > 15 to simulate an early hand in without answered questions
            component.remainingTimeSeconds = 200;

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBeNull();

            submitButton.click();
            fixture.changeDetectorRef.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/quiz/exercises/${quizExercise.id}/submissions/live`);
            fixture.changeDetectorRef.detectChanges();

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBeFalse();
        });

        it('should show results after ending', () => {
            fixture.detectChanges();

            const answer: SubmittedAnswer = { scoreInPoints: 1, quizQuestion: question2 };
            const quizSubmission: QuizSubmission = {
                submissionDate: now.subtract(3, 'minutes'),
                submittedAnswers: [answer],
                scoreInPoints: 1,
            };
            const result: Result = { submission: quizSubmission };
            quizSubmission.results = [result];
            const participation: StudentParticipation = { exercise: quizExerciseForResults, submissions: [quizSubmission] };
            component.showQuizResultAfterQuizEnd(participation);

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.questionScores[question2.id!]).toBe(answer.scoreInPoints);
            expect(component.userScore).toBe(quizSubmission.scoreInPoints);
            expect(component.showingResult).toBeTrue();
        });

        it('should mark changes as unsaved when an answer changes', () => {
            fixture.detectChanges();
            component.onSelectionChanged();
            expect(component.unsavedChanges).toBeTrue();
        });

        it('should react to errors', () => {
            const alertService = fixture.debugElement.injector.get(AlertService);
            const alertSpy = jest.spyOn(alertService, 'addAlert');
            fixture.detectChanges();

            component.onSubmitError({ message: 'error' } as any);
            expect(component.isSubmitting).toBeFalse();

            component.onSaveError('error');
            expect(component.isSubmitting).toBeFalse();
            expect(component.unsavedChanges).toBeTrue();

            expect(alertSpy).toHaveBeenCalledTimes(2);
        });

        it('should express timespan in humanized text', () => {
            expect(component.relativeTimeText(100020)).toBe('1667 min');
            expect(component.relativeTimeText(60)).toBe('1 min 0 s');
            expect(component.relativeTimeText(5)).toBe('5 s');
        });

        it('should adjust release date of the quiz if it didnt start', () => {
            const releaseDate = dayjs().add(1, 'minutes');
            const timeUntilPlannedStart = 10;
            const quizToApply = {
                ...quizExercise,
                started: false,
                isPlannedToStart: true,
                releaseDate,
                timeUntilPlannedStart,
            };

            component.applyQuizFull(quizToApply);
            expect(component.quizExercise).toEqual(quizToApply);
            expect(component.quizExercise.releaseDate!.toString()).toBe(releaseDate.toString());
        });

        it('should apply participation', () => {
            const submission: QuizSubmission = {
                id: 1,
                submissionDate: dayjs().subtract(10, 'minutes'),
                submittedAnswers: [],
            };
            const result: Result = { id: 1, submission };
            submission.results = [result];
            const endedQuizExercise = { ...quizExercise, quizEnded: true };
            const participation: StudentParticipation = { exercise: endedQuizExercise, submissions: [submission] };

            component.quizExercise = quizExercise;
            component.updateParticipationFromServer(participation);

            expect(component.submission.id).toBe(submission.id);
            expect(component.quizExercise.quizEnded).toBeTrue();
        });
    });

    describe('preview mode', () => {
        beforeEach(() => {
            MockBuilder(QuizParticipationComponent)
                .keep(FaIconComponent)
                .keep(MultipleChoiceQuestionComponent)
                .keep(DragAndDropQuestionComponent)
                .keep(ShortAnswerQuestionComponent)
                .keep(ButtonComponent)
                .keep(QuizParticipationService)
                .keep(ArtemisQuizService)
                .keep(SubmissionService)
                .keep(AlertService)
                .keep(ArtemisServerDateService)
                .keep(Router)
                .provide(provideHttpClient())
                .provide(provideHttpClientTesting())
                .provide({ provide: TranslateService, useClass: MockTranslateService })
                .provide(LocalStorageService)
                .provide(SessionStorageService)
                .provide({ provide: WebsocketService, useClass: MockWebsocketService })
                .provide({
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ exerciseId: quizExercise.id }),
                        data: of({ mode: 'preview' }),
                    },
                })
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(() => {
            httpMock.verify();
            jest.restoreAllMocks();
        });

        afterEach(fakeAsync(() => {
            discardPeriodicTasks();
        }));

        it('should initialize', () => {
            const serviceStub = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();
            expect(serviceStub).toHaveBeenCalledWith(quizExercise.id);
        });

        it('should initialize and start', () => {
            const quizService = fixture.debugElement.injector.get(ArtemisQuizService);
            const serviceSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            const startSpy = jest.spyOn(component, 'startQuizPreviewOrPractice');
            const randomizeSpy = jest.spyOn(quizService, 'randomizeOrder');
            fixture.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(startSpy).toHaveBeenCalledOnce();
            expect(randomizeSpy).toHaveBeenCalledOnce();
        });

        it('should submit quiz', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBeNull();

            submitButton.click();
            fixture.changeDetectorRef.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submission: { submissionDate: now, submitted: true } as QuizSubmission } as Result);
            expect(request.request.url).toBe(`api/quiz/exercises/${quizExercise.id}/submissions/preview`);
            fixture.changeDetectorRef.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExercise.id);
        });
    });

    describe('practice mode', () => {
        beforeEach(() => {
            MockBuilder(QuizParticipationComponent)
                .keep(FaIconComponent)
                .keep(MultipleChoiceQuestionComponent)
                .keep(DragAndDropQuestionComponent)
                .keep(ShortAnswerQuestionComponent)
                .keep(ButtonComponent)
                .keep(QuizParticipationService)
                .keep(ArtemisQuizService)
                .keep(SubmissionService)
                .keep(AlertService)
                .keep(ArtemisServerDateService)
                .keep(QuizExerciseService)
                .mock(WebsocketService)
                .provide(provideHttpClient())
                .provide(provideHttpClientTesting())
                .provide({ provide: TranslateService, useClass: MockTranslateService })
                .provide(LocalStorageService)
                .provide(SessionStorageService)
                .provide({ provide: Router, useClass: MockRouter })
                .provide({
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: 1, exerciseId: quizExerciseForPractice.id }),
                        data: of({ mode: 'practice' }),
                    },
                })
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(() => {
            httpMock.verify();
            jest.restoreAllMocks();
        });

        afterEach(fakeAsync(() => {
            discardPeriodicTasks();
        }));

        it('should initialize', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
        });

        it('should initialize and start', () => {
            const quizService = fixture.debugElement.injector.get(ArtemisQuizService);
            const serviceSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            const startSpy = jest.spyOn(component, 'startQuizPreviewOrPractice');
            const randomizeSpy = jest.spyOn(quizService, 'randomizeOrder');
            fixture.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
            expect(startSpy).toHaveBeenCalledOnce();
            expect(randomizeSpy).toHaveBeenCalledOnce();
        });

        it('should submit quiz', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBeNull();

            submitButton.click();
            fixture.changeDetectorRef.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            const quizSubmission: QuizSubmission = { submissionDate: now, submitted: true };
            request.flush({
                submission: quizSubmission,
                participation: { exercise: quizExerciseForPractice } as StudentParticipation,
            } as Result);
            expect(request.request.url).toBe(`api/quiz/exercises/${quizExerciseForPractice.id}/submissions/practice`);
            fixture.changeDetectorRef.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
        });
    });

    describe('solution mode', () => {
        beforeEach(() => {
            MockBuilder(QuizParticipationComponent)
                .keep(FaIconComponent)
                .keep(MultipleChoiceQuestionComponent)
                .keep(DragAndDropQuestionComponent)
                .keep(ShortAnswerQuestionComponent)
                .keep(ButtonComponent)
                .keep(QuizParticipationService)
                .keep(ArtemisQuizService)
                .keep(SubmissionService)
                .keep(AlertService)
                .keep(ArtemisServerDateService)
                .keep(Router)
                .provide(provideHttpClient())
                .provide(provideHttpClientTesting())
                .provide({ provide: TranslateService, useClass: MockTranslateService })
                .provide(LocalStorageService)
                .provide(SessionStorageService)
                .provide({ provide: WebsocketService, useClass: MockWebsocketService })
                .provide({
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: 1, exerciseId: quizExerciseForPractice.id }),
                        data: of({ mode: 'solution' }),
                    },
                })
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
                    resultForSolutionServiceSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
                });
        });

        afterEach(() => {
            jest.restoreAllMocks();
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
            // TODO: this test is really weird in how it tests things and should probably be re-written
            fixture.detectChanges();

            // Test the error branches first
            component.quizExercise = {} as QuizExercise;
            fixture.changeDetectorRef.detectChanges();

            component.updateDisplayedTimes();
            fixture.changeDetectorRef.detectChanges();

            expect(component.remainingTimeSeconds).toBe(0);
            expect(component.remainingTimeText).toBe('?');
            expect(component.timeUntilStart).toBe('');

            // Now test the remaining non-error branches
            component.quizExercise = quizExerciseUnreleased;
            component.updateDisplayedTimes();
            fixture.changeDetectorRef.detectChanges();

            component.quizExercise = quizExerciseForResults;
            component.endDate = component.quizExercise.dueDate;
            component.submission = { submissionDate: now, submitted: true } as QuizSubmission;
            component.updateDisplayedTimes();
            fixture.changeDetectorRef.detectChanges();

            expect(component.remainingTimeText).toBe('artemisApp.showStatistic.quizHasEnded');
            expect(component.timeUntilStart).toBe('');
        });
    });
});
