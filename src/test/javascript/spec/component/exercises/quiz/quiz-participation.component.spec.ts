import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { SubmittedAnswer } from 'app/entities/quiz/submitted-answer.model';
import { Result } from 'app/entities/result.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizParticipationComponent } from 'app/exercises/quiz/participate/quiz-participation.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ButtonComponent } from 'app/shared/components/button.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';

// Store a copy of now to avoid timing issues
const now = dayjs();
const question1: QuizQuestion = { id: 1, type: QuizQuestionType.DRAG_AND_DROP, points: 1, invalid: false, exportQuiz: false, randomizeOrder: true };
const question2: MultipleChoiceQuestion = { id: 2, type: QuizQuestionType.MULTIPLE_CHOICE, points: 2, answerOptions: [], invalid: false, exportQuiz: false, randomizeOrder: true };
const question3: QuizQuestion = { id: 3, type: QuizQuestionType.SHORT_ANSWER, points: 3, invalid: false, exportQuiz: false, randomizeOrder: true };

const quizExercise: QuizExercise = {
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
const quizExerciseForPractice: QuizExercise = {
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(4, 'minutes'),
    dueDate: dayjs(now).subtract(2, 'minutes'),
    quizStarted: true,
    quizEnded: true,
    isOpenForPractice: true,
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

const testBedDeclarations = [
    QuizParticipationComponent,
    ButtonComponent,
    MockPipe(ArtemisTranslatePipe),
    MockPipe(ArtemisDatePipe),
    MockDirective(TranslateDirective),
    MockComponent(MultipleChoiceQuestionComponent),
    MockComponent(DragAndDropQuestionComponent),
    MockComponent(ShortAnswerQuestionComponent),
    MockComponent(JhiConnectionStatusComponent),
    MockDirective(NgbTooltip),
    MockDirective(FeatureToggleDirective),
];

describe('QuizParticipationComponent', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let participationSpy: jest.SpyInstance;
    let resultForSolutionServiceSpy: jest.SpyInstance;
    let httpMock: HttpTestingController;
    let exerciseService: QuizExerciseService;

    describe('live mode', () => {
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, HttpClientTestingModule],
                declarations: testBedDeclarations,
                providers: [
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            params: of({ courseId: 1, exerciseId: quizExercise.id }),
                            data: of({ mode: 'live' }),
                        },
                    },
                    {
                        provide: LocalStorageService,
                        useClass: MockLocalStorageService,
                    },
                    {
                        provide: SessionStorageService,
                        useClass: MockSyncStorage,
                    },
                    {
                        provide: TranslateService,
                        useClass: MockTranslateService,
                    },
                    {
                        provide: JhiWebsocketService,
                        useClass: MockWebsocketService,
                    },
                ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    const participationService = fixture.debugElement.injector.get(ParticipationService);
                    const participation: StudentParticipation = { exercise: { ...quizExercise } };
                    participationSpy = jest
                        .spyOn(participationService, 'findParticipationForCurrentUser')
                        .mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(() => {
            httpMock.verify();
            jest.restoreAllMocks();
        });

        afterEach(fakeAsync(function () {
            discardPeriodicTasks();
        }));

        it('should initialize', () => {
            fixture.detectChanges();
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
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

        it('should update in intervals', fakeAsync(() => {
            fixture.detectChanges();

            const updateSpy = jest.spyOn(component, 'updateDisplayedTimes');
            tick(5000);
            fixture.detectChanges();
            discardPeriodicTasks();

            expect(updateSpy).toHaveBeenCalledTimes(50);
        }));

        it('should check quiz end in intervals', fakeAsync(() => {
            fixture.detectChanges();

            const checkQuizEndSpy = jest.spyOn(component, 'checkForQuizEnd');
            tick(5000);
            fixture.detectChanges();
            discardPeriodicTasks();

            expect(checkQuizEndSpy).toHaveBeenCalledTimes(50);
        }));

        it('should add alert on quiz end', fakeAsync(() => {
            fixture.detectChanges();

            component.endDate = dayjs().add(1, 'seconds');
            component.quizExercise.quizMode = QuizMode.BATCHED;
            component.submission.submissionDate = dayjs();

            const alertService = fixture.debugElement.injector.get(AlertService);
            const alertSpy = jest.spyOn(alertService, 'success');

            const checkQuizEndSpy = jest.spyOn(component, 'checkForQuizEnd');

            tick(2000);
            fixture.detectChanges();
            discardPeriodicTasks();

            expect(checkQuizEndSpy).toHaveBeenCalledTimes(20);
            expect(alertSpy).toHaveBeenCalledTimes(20);
        }));

        it('should refresh quiz', () => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            fixture.detectChanges();

            component.quizExercise.quizStarted = false;
            component.quizBatch!.started = false;
            component.quizBatch!.startTime = undefined;

            // Returns the started exercise
            const findStudentSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

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
        ])('should join %s batches that have started %p', (quizMode, started) => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            const participationService = fixture.debugElement.injector.get(ParticipationService);
            const participation: StudentParticipation = { exercise: { ...quizExercise, quizBatches: [], quizMode, quizStarted: false } as QuizExercise };
            participationSpy = jest
                .spyOn(participationService, 'findParticipationForCurrentUser')
                .mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));

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
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBeNull();

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBeFalse();
        });

        it('should return true if student didnt interact with any question', () => {
            component.quizExercise = { ...quizExercise, quizQuestions: undefined };
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
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBeFalse();
        });

        it('should show results after ending', () => {
            fixture.detectChanges();

            const answer: SubmittedAnswer = { scoreInPoints: 1, quizQuestion: question2 };
            const quizSubmission: QuizSubmission = { submissionDate: now.subtract(3, 'minutes'), submittedAnswers: [answer], scoreInPoints: 1 };
            const result: Result = { submission: quizSubmission };
            const participation: StudentParticipation = { exercise: quizExerciseForResults, results: [result] };
            component.showQuizResultAfterQuizEnd(participation);

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.questionScores[question2.id!]).toBe(answer.scoreInPoints);
            expect(component.userScore).toBe(quizSubmission.scoreInPoints);
            expect(component.showingResult).toBeTrue();
        });

        it('should update on selection changes', () => {
            const webSocketService = fixture.debugElement.injector.get(JhiWebsocketService);
            const webSocketSpy = jest.spyOn(webSocketService, 'send').mockImplementation();
            const applySpy = jest.spyOn(component, 'applySelection');
            fixture.detectChanges();

            component.onSelectionChanged();

            expect(applySpy).toHaveBeenCalledOnce();
            expect(webSocketSpy).toHaveBeenCalledOnce();
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

            expect(alertSpy).toHaveBeenCalledOnce();
        });

        it('should express timespan in humanized text', () => {
            expect(component.relativeTimeText(100020)).toBe('1667 min');
            expect(component.relativeTimeText(60)).toBe('1 min 0 s');
            expect(component.relativeTimeText(5)).toBe('5 s');
        });

        it('should adjust release date of the quiz if it didnt start', () => {
            const releaseDate = dayjs().add(1, 'minutes');
            const timeUntilPlannedStart = 10;
            const quizToApply = { ...quizExercise, started: false, isPlannedToStart: true, releaseDate, timeUntilPlannedStart };

            component.applyQuizFull(quizToApply);
            expect(component.quizExercise).toEqual(quizToApply);
            expect(component.quizExercise.releaseDate!.toString()).toBe(releaseDate.toString());
        });

        it('should apply participation', () => {
            const submission: QuizSubmission = { id: 1, submissionDate: dayjs().subtract(10, 'minutes'), submittedAnswers: [] };
            const result: Result = { id: 1, submission };
            const endedQuizExercise = { ...quizExercise, quizEnded: true };
            const participation: StudentParticipation = { exercise: endedQuizExercise, results: [result] };

            component.quizExercise = quizExercise;
            component.updateParticipationFromServer(participation);

            expect(component.submission.id).toBe(submission.id);
            expect(component.quizExercise.quizEnded).toBeTrue();
        });
    });

    describe('preview mode', () => {
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, HttpClientTestingModule],
                declarations: testBedDeclarations,
                providers: [
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            params: of({ courseId: 1, exerciseId: quizExercise.id }),
                            data: of({ mode: 'preview' }),
                        },
                    },
                    {
                        provide: LocalStorageService,
                        useClass: MockLocalStorageService,
                    },
                    {
                        provide: SessionStorageService,
                        useClass: MockSyncStorage,
                    },
                    {
                        provide: TranslateService,
                        useClass: MockTranslateService,
                    },
                ],
            })
                .compileComponents()
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

        it('should initialize', () => {
            const serviceStub = jest.spyOn(exerciseService, 'find').mockImplementation();
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
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submission: { submissionDate: now, submitted: true } as QuizSubmission } as Result);
            expect(request.request.url).toBe(`api/exercises/${quizExercise.id}/submissions/preview`);
            fixture.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExercise.id);
        });
    });

    describe('practice mode', () => {
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, HttpClientTestingModule],
                declarations: testBedDeclarations,
                providers: [
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            params: of({ courseId: 1, exerciseId: quizExerciseForPractice.id }),
                            data: of({ mode: 'practice' }),
                        },
                    },
                    {
                        provide: LocalStorageService,
                        useClass: MockLocalStorageService,
                    },
                    {
                        provide: SessionStorageService,
                        useClass: MockSyncStorage,
                    },
                    {
                        provide: TranslateService,
                        useClass: MockTranslateService,
                    },
                ],
            })
                .compileComponents()
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

        it('should initialize', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'findForStudent').mockImplementation();
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
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            const quizSubmission: QuizSubmission = { submissionDate: now, submitted: true };
            request.flush({ submission: quizSubmission, participation: { exercise: quizExerciseForPractice } as StudentParticipation } as Result);
            expect(request.request.url).toBe(`api/exercises/${quizExerciseForPractice.id}/submissions/practice`);
            fixture.detectChanges();

            expect(serviceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
        });
    });

    describe('solution mode', () => {
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, HttpClientTestingModule],
                declarations: testBedDeclarations,
                providers: [
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            params: of({ courseId: 1, exerciseId: quizExerciseForPractice.id }),
                            data: of({ mode: 'solution' }),
                        },
                    },
                    {
                        provide: LocalStorageService,
                        useClass: MockLocalStorageService,
                    },
                    {
                        provide: SessionStorageService,
                        useClass: MockSyncStorage,
                    },
                    {
                        provide: TranslateService,
                        useClass: MockTranslateService,
                    },
                ],
            })
                .compileComponents()
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
            fixture.detectChanges();

            component.updateDisplayedTimes();
            fixture.detectChanges();

            expect(component.remainingTimeSeconds).toBe(0);
            expect(component.remainingTimeText).toBe('?');
            expect(component.timeUntilStart).toBe('');

            // Now test the remaining non-error branches
            component.quizExercise = quizExerciseUnreleased;
            component.updateDisplayedTimes();
            fixture.detectChanges();

            component.quizExercise = quizExerciseForResults;
            component.endDate = component.quizExercise.dueDate;
            component.submission = { submissionDate: now, submitted: true } as QuizSubmission;
            component.updateDisplayedTimes();
            fixture.detectChanges();

            expect(component.remainingTimeText).toBe('showStatistic.quizHasEnded');
            expect(component.timeUntilStart).toBe('');
        });
    });
});
