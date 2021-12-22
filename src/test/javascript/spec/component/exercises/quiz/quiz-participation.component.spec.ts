import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
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
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import dayjs from 'dayjs';
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

// Store a copy of now to avoid timing issues
const now = dayjs();
const question1 = { id: 1, type: QuizQuestionType.DRAG_AND_DROP, points: 1 } as QuizQuestion;
const question2 = { id: 2, type: QuizQuestionType.MULTIPLE_CHOICE, points: 2, answerOptions: [] } as QuizQuestion;
const question3 = { id: 3, type: QuizQuestionType.SHORT_ANSWER, points: 3 } as QuizQuestion;

const quizExercise = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(2, 'minutes'),
    adjustedReleaseDate: dayjs(now).subtract(2, 'minutes'),
    dueDate: dayjs(now).add(2, 'minutes'),
    adjustedDueDate: dayjs(now).add(2, 'minutes'),
    started: true,
}) as QuizExercise;
const quizExerciseForPractice = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(4, 'minutes'),
    adjustedReleaseDate: dayjs(now).subtract(4, 'minutes'),
    dueDate: dayjs(now).subtract(2, 'minutes'),
    adjustedDueDate: dayjs(now).subtract(2, 'minutes'),
    isOpenForPractice: true,
}) as QuizExercise;
const quizExerciseForResults = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).subtract(4, 'minutes'),
    adjustedReleaseDate: dayjs(now).subtract(4, 'minutes'),
    dueDate: dayjs(now).subtract(2, 'minutes'),
    adjustedDueDate: dayjs(now).subtract(2, 'minutes'),
    ended: true,
}) as QuizExercise;
const quizExerciseUnreleased = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: dayjs(now).add(2, 'days'),
    adjustedReleaseDate: dayjs(now).add(2, 'days'),
    dueDate: dayjs(now).add(4, 'days'),
    adjustedDueDate: dayjs(now).add(4, 'days'),
    ended: true,
}) as QuizExercise;

const testBedDeclarations = [
    QuizParticipationComponent,
    ButtonComponent,
    MockComponent(AlertComponent),
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
                ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(QuizParticipationComponent);
                    component = fixture.componentInstance;

                    const participationService = fixture.debugElement.injector.get(ParticipationService);
                    const participation = { exercise: quizExercise } as StudentParticipation;
                    participationSpy = jest
                        .spyOn(participationService, 'findParticipationForCurrentUser')
                        .mockReturnValue(of({ body: participation } as HttpResponse<StudentParticipation>));
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(function () {
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
            expect(component.waitingForQuizStart).toBe(false);
            expect(component.totalScore).toBe(6);
            expect(component.dragAndDropMappings.get(question1.id!)).toEqual([]);
            expect(component.selectedAnswerOptions.get(question2.id!)).toEqual([]);
            expect(component.shortAnswerSubmittedTexts.get(question3.id!)).toEqual([]);
            expect(component.submission).not.toBe(null);
        });

        it('should update in intervals', fakeAsync(() => {
            fixture.detectChanges();

            const updateSpy = jest.spyOn(component, 'updateDisplayedTimes');
            tick(5000);
            fixture.detectChanges();
            discardPeriodicTasks();

            expect(updateSpy).toHaveBeenCalled();
        }));

        it('should refresh quiz', () => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            fixture.detectChanges();

            component.quizExercise.started = false;

            // Returns the started exercise
            const findStudentSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const refreshButton = fixture.debugElement.nativeElement.querySelector('#refresh-quiz button');
            expect(refreshButton).not.toBe(null);

            refreshButton.click();
            fixture.detectChanges();

            expect(findStudentSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
        });

        it('should submit quiz', () => {
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBe(null);

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBe(false);
        });

        it('should return true if student didnt interact with any question', () => {
            component.quizExercise = { ...quizExercise, quizQuestions: undefined };
            expect(component.areAllQuestionsAnswered()).toBe(true);

            component.quizExercise = quizExercise;
            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.selectedAnswerOptions.set(2, []);
            expect(component.areAllQuestionsAnswered()).toBe(false);

            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
            component.dragAndDropMappings.set(1, []);
            expect(component.areAllQuestionsAnswered()).toBe(false);

            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
            component.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
            component.shortAnswerSubmittedTexts.set(3, []);
            expect(component.areAllQuestionsAnswered()).toBe(false);
        });

        it('should show warning on submit', () => {
            const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
            fixture.detectChanges();

            // Set a value > 15 to simulate an early hand in without answered questions
            component.remainingTimeSeconds = 200;

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBe(null);

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).toBe(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(confirmSpy).toHaveBeenCalled();
            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.isSubmitting).toBe(false);
        });

        it('should show results after ending', () => {
            fixture.detectChanges();

            const answer = { scoreInPoints: 1, quizQuestion: question2 } as SubmittedAnswer;
            const quizSubmission = { submissionDate: now.subtract(3, 'minutes'), submittedAnswers: [answer], scoreInPoints: 1 } as QuizSubmission;
            const result = { resultString: 'result!', submission: quizSubmission } as Result;
            const participation = { exercise: quizExerciseForResults, results: [result] } as StudentParticipation;
            component.showQuizResultAfterQuizEnd(participation);

            expect(participationSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(component.questionScores[question2.id!]).toBe(answer.scoreInPoints);
            expect(component.userScore).toBe(quizSubmission.scoreInPoints);
            expect(component.showingResult).toBe(true);
        });

        it('should update on selection changes', () => {
            const webSocketService = fixture.debugElement.injector.get(JhiWebsocketService);
            const webSocketSpy = jest.spyOn(webSocketService, 'send').mockImplementation();
            const applySpy = jest.spyOn(component, 'applySelection');
            fixture.detectChanges();

            component.onSelectionChanged();

            expect(applySpy).toHaveBeenCalled();
            expect(webSocketSpy).toHaveBeenCalled();
        });

        it('should react to errors', () => {
            const alertService = fixture.debugElement.injector.get(AlertService);
            const alertSpy = jest.spyOn(alertService, 'error').mockReturnValue({ msg: '' } as any);
            fixture.detectChanges();

            component.onSubmitError({ message: 'error' } as any);
            expect(component.isSubmitting).toBe(false);

            component.onSaveError('error');
            expect(component.isSubmitting).toBe(false);
            expect(component.unsavedChanges).toBe(true);

            expect(alertSpy).toHaveBeenCalled();
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
            const result: Result = { id: 1, submission, resultString: 'result-string' };
            const endedQuizExercise = { ...quizExercise, ended: true };
            const participation: StudentParticipation = { exercise: endedQuizExercise, results: [result] };

            component.quizExercise = quizExercise;
            component.timeDifference = 10;
            component.updateParticipationFromServer(participation);

            expect(component.submission.id).toBe(submission.id);
            expect(component.quizExercise.ended).toBe(true);
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

        afterEach(function () {
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
            expect(startSpy).toHaveBeenCalled();
            expect(randomizeSpy).toHaveBeenCalled();
        });

        it('should submit quiz', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBe(null);

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

        afterEach(function () {
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
            expect(startSpy).toHaveBeenCalled();
            expect(randomizeSpy).toHaveBeenCalled();
        });

        it('should submit quiz', () => {
            const serviceSpy = jest.spyOn(exerciseService, 'findForStudent').mockReturnValue(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz button');
            expect(submitButton).not.toBe(null);

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            const quizSubmission = { submissionDate: now, submitted: true } as QuizSubmission;
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

        afterEach(function () {
            jest.restoreAllMocks();
        });

        it('should initialize', () => {
            fixture.detectChanges();
        });

        it('should initialize and show solution', () => {
            fixture.detectChanges();

            expect(resultForSolutionServiceSpy).toHaveBeenCalledWith(quizExerciseForPractice.id);
            expect(component.showingResult).toBe(true);
            expect(component.totalScore).toBe(6);
        });

        it('should update time', () => {
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
            component.submission = { submissionDate: now, submitted: true } as QuizSubmission;
            component.updateDisplayedTimes();
            fixture.detectChanges();

            expect(component.remainingTimeText).toBe('showStatistic.quizhasEnded');
            expect(component.timeUntilStart).toBe('showStatistic.now');
        });
    });
});
