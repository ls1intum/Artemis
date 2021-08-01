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
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/core/util/alert.service';

chai.use(sinonChai);
const expect = chai.expect;

// Store a copy of now to avoid timing issues
const now = moment();
const question1 = { id: 1, type: QuizQuestionType.DRAG_AND_DROP, points: 1 } as QuizQuestion;
const question2 = { id: 2, type: QuizQuestionType.MULTIPLE_CHOICE, points: 2, answerOptions: [] } as QuizQuestion;
const question3 = { id: 3, type: QuizQuestionType.SHORT_ANSWER, points: 3 } as QuizQuestion;

const quizExercise = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: moment(now).subtract(2, 'minutes'),
    adjustedReleaseDate: moment(now).subtract(2, 'minutes'),
    dueDate: moment(now).add(2, 'minutes'),
    adjustedDueDate: moment(now).add(2, 'minutes'),
    started: true,
}) as QuizExercise;
const quizExerciseForPractice = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: moment(now).subtract(4, 'minutes'),
    adjustedReleaseDate: moment(now).subtract(4, 'minutes'),
    dueDate: moment(now).subtract(2, 'minutes'),
    adjustedDueDate: moment(now).subtract(2, 'minutes'),
    isOpenForPractice: true,
}) as QuizExercise;
const quizExerciseForResults = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: moment(now).subtract(4, 'minutes'),
    adjustedReleaseDate: moment(now).subtract(4, 'minutes'),
    dueDate: moment(now).subtract(2, 'minutes'),
    adjustedDueDate: moment(now).subtract(2, 'minutes'),
    ended: true,
}) as QuizExercise;
const quizExerciseUnreleased = (<any>{
    id: 1,
    quizQuestions: [question1, question2, question3],
    releaseDate: moment(now).add(2, 'days'),
    adjustedReleaseDate: moment(now).add(2, 'days'),
    dueDate: moment(now).add(4, 'days'),
    adjustedDueDate: moment(now).add(4, 'days'),
    ended: true,
}) as QuizExercise;

const testBedDeclarations = [
    QuizParticipationComponent,
    MockComponent(AlertComponent),
    MockPipe(ArtemisTranslatePipe),
    MockPipe(ArtemisDatePipe),
    MockDirective(TranslateDirective),
    MockComponent(MultipleChoiceQuestionComponent),
    MockComponent(DragAndDropQuestionComponent),
    MockComponent(ShortAnswerQuestionComponent),
    MockComponent(ButtonComponent),
    MockComponent(JhiConnectionStatusComponent),
    MockDirective(NgbTooltip),
];

describe('QuizParticipationComponent', () => {
    let fixture: ComponentFixture<QuizParticipationComponent>;
    let component: QuizParticipationComponent;
    let participationStub: sinon.SinonStub;
    let resultForSolutionServiceStub: sinon.SinonStub;
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
                    participationStub = sinon.stub(participationService, 'findParticipation').returns(of({ body: participation } as HttpResponse<StudentParticipation>));
                    httpMock = fixture.debugElement.injector.get(HttpTestingController);
                });
        });

        afterEach(function () {
            httpMock.verify();
            sinon.restore();
        });

        afterEach(fakeAsync(function () {
            discardPeriodicTasks();
        }));

        it('should initialize', () => {
            fixture.detectChanges();
            expect(component).to.be.ok;
            expect(participationStub).to.have.been.calledWith(quizExercise.id);
        });

        it('should fetch exercise and create a new submission', () => {
            fixture.detectChanges();

            expect(participationStub).to.have.been.calledWith(quizExercise.id);
            expect(component.quizExercise).to.equal(quizExercise);
            expect(component.waitingForQuizStart).to.be.false;
            expect(component.totalScore).to.equal(6);
            expect(component.dragAndDropMappings.get(question1.id!)).to.deep.equal([]);
            expect(component.selectedAnswerOptions.get(question2.id!)).to.deep.equal([]);
            expect(component.shortAnswerSubmittedTexts.get(question3.id!)).to.deep.equal([]);
            expect(component.submission).to.be.ok;
        });

        it('should update in intervals', fakeAsync(() => {
            fixture.detectChanges();

            const updateSpy = sinon.spy(component, 'updateDisplayedTimes');
            tick(5000);
            fixture.detectChanges();
            discardPeriodicTasks();

            expect(updateSpy).to.have.been.called;
        }));

        it('should refresh quiz', () => {
            exerciseService = fixture.debugElement.injector.get(QuizExerciseService);
            fixture.detectChanges();

            component.quizExercise.started = false;

            // Returns the started exercise
            const findStudentStub = sinon.stub(exerciseService, 'findForStudent').returns(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const refreshButton = fixture.debugElement.nativeElement.querySelector('#refresh-quiz');
            expect(refreshButton).to.exist;

            refreshButton.click();
            fixture.detectChanges();

            expect(findStudentStub).to.have.been.calledWith(quizExercise.id);
            expect(participationStub).to.have.been.calledWith(quizExercise.id);
        });

        it('should submit quiz', () => {
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz');
            expect(submitButton).to.exist;

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).to.equal(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(participationStub).to.have.been.calledWith(quizExercise.id);
            expect(component.isSubmitting).to.be.false;
        });

        it('should return true if student didnt interact with any question', () => {
            component.quizExercise = { ...quizExercise, quizQuestions: undefined };
            expect(component.areAllQuestionsAnswered()).to.be.true;

            component.quizExercise = quizExercise;
            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.selectedAnswerOptions.set(2, []);
            expect(component.areAllQuestionsAnswered()).to.be.false;

            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
            component.dragAndDropMappings.set(1, []);
            expect(component.areAllQuestionsAnswered()).to.be.false;

            component.selectedAnswerOptions = new Map<number, AnswerOption[]>();
            component.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
            component.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
            component.shortAnswerSubmittedTexts.set(3, []);
            expect(component.areAllQuestionsAnswered()).to.be.false;
        });

        it('should show warning on submit', () => {
            const confirmStub = sinon.stub(window, 'confirm').returns(true);
            fixture.detectChanges();

            // Set a value > 15 to simulate an early hand in without answered questions
            component.remainingTimeSeconds = 200;

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz');
            expect(submitButton).to.exist;

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submissionDate: now } as QuizSubmission);
            expect(request.request.url).to.equal(`api/exercises/${quizExercise.id}/submissions/live`);
            fixture.detectChanges();

            expect(confirmStub).to.have.been.called;
            expect(participationStub).to.have.been.calledWith(quizExercise.id);
            expect(component.isSubmitting).to.be.false;
        });

        it('should show results after ending', () => {
            fixture.detectChanges();

            const answer = { scoreInPoints: 1, quizQuestion: question2 } as SubmittedAnswer;
            const quizSubmission = { submissionDate: now.subtract(3, 'minutes'), submittedAnswers: [answer], scoreInPoints: 1 } as QuizSubmission;
            const result = { resultString: 'result!', submission: quizSubmission } as Result;
            const participation = { exercise: quizExerciseForResults, results: [result] } as StudentParticipation;
            component.showQuizResultAfterQuizEnd(participation);

            expect(participationStub).to.have.been.calledWith(quizExercise.id);
            expect(component.questionScores[question2.id!]).to.equal(answer.scoreInPoints);
            expect(component.userScore).to.equal(quizSubmission.scoreInPoints);
            expect(component.showingResult).to.be.true;
        });

        it('should update on selection changes', () => {
            const webSocketService = fixture.debugElement.injector.get(JhiWebsocketService);
            const webSocketStub = sinon.stub(webSocketService, 'send');
            const applySpy = sinon.spy(component, 'applySelection');
            fixture.detectChanges();

            component.onSelectionChanged();

            expect(applySpy).to.have.been.called;
            expect(webSocketStub).to.have.been.called;
        });

        it('should react to errors', () => {
            const alertService = fixture.debugElement.injector.get(AlertService);
            const alertStub = sinon.stub(alertService, 'error').returns({ msg: '' } as any);
            fixture.detectChanges();

            component.onSubmitError({ message: 'error' } as any);
            expect(component.isSubmitting).to.be.false;

            component.onSaveError('error');
            expect(component.isSubmitting).to.be.false;
            expect(component.unsavedChanges).to.be.true;

            expect(alertStub).to.have.been.called;
        });

        it('should express timespan in humanized text', () => {
            expect(component.relativeTimeText(100020)).to.equal('1667 min');
            expect(component.relativeTimeText(60)).to.equal('1 min 0 s');
            expect(component.relativeTimeText(5)).to.equal('5 s');
        });

        it('should adjust release date of the quiz if it didnt start', () => {
            const releaseDate = moment().add(1, 'minutes');
            const timeUntilPlannedStart = 10;
            const quizToApply = { ...quizExercise, started: false, isPlannedToStart: true, releaseDate, timeUntilPlannedStart };

            component.applyQuizFull(quizToApply);
            expect(component.quizExercise).to.equal(quizToApply);
            expect(component.quizExercise.releaseDate!.toString()).to.equal(releaseDate.toString());
        });

        it('should apply participation', () => {
            const submission: QuizSubmission = { id: 1, submissionDate: moment().subtract(10, 'minutes'), submittedAnswers: [] };
            const result: Result = { id: 1, submission, resultString: 'result-string' };
            const endedQuizExercise = { ...quizExercise, ended: true };
            const participation: StudentParticipation = { exercise: endedQuizExercise, results: [result] };

            component.quizExercise = quizExercise;
            component.timeDifference = 10;
            component.updateParticipationFromServer(participation);

            expect(component.submission.id).to.equal(submission.id);
            expect(component.quizExercise.ended).to.be.true;
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
            sinon.restore();
        });

        it('should initialize', () => {
            const serviceStub = sinon.stub(exerciseService, 'find');
            fixture.detectChanges();

            expect(component).to.be.ok;
            expect(serviceStub).to.have.been.calledWith(quizExercise.id);
        });

        it('should initialize and start', () => {
            const quizService = fixture.debugElement.injector.get(ArtemisQuizService);
            const serviceStub = sinon.stub(exerciseService, 'find').returns(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            const startSpy = sinon.spy(component, 'startQuizPreviewOrPractice');
            const randomizeStub = sinon.stub(quizService, 'randomizeOrder');
            fixture.detectChanges();

            expect(component).to.be.ok;
            expect(serviceStub).to.have.been.calledWith(quizExercise.id);
            expect(startSpy).to.have.been.called;
            expect(randomizeStub).to.have.been.called;
        });

        it('should submit quiz', () => {
            const serviceStub = sinon.stub(exerciseService, 'find').returns(of({ body: quizExercise } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz');
            expect(submitButton).to.exist;

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            request.flush({ submission: { submissionDate: now, submitted: true } as QuizSubmission } as Result);
            expect(request.request.url).to.equal(`api/exercises/${quizExercise.id}/submissions/preview`);
            fixture.detectChanges();

            expect(serviceStub).to.have.been.calledWith(quizExercise.id);
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
            sinon.restore();
        });

        it('should initialize', () => {
            const serviceStub = sinon.stub(exerciseService, 'findForStudent');
            fixture.detectChanges();

            expect(component).to.be.ok;
            expect(serviceStub).to.have.been.calledWith(quizExerciseForPractice.id);
        });

        it('should initialize and start', () => {
            const quizService = fixture.debugElement.injector.get(ArtemisQuizService);
            const serviceStub = sinon.stub(exerciseService, 'findForStudent').returns(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            const startSpy = sinon.spy(component, 'startQuizPreviewOrPractice');
            const randomizeStub = sinon.stub(quizService, 'randomizeOrder');
            fixture.detectChanges();

            expect(component).to.be.ok;
            expect(serviceStub).to.have.been.calledWith(quizExerciseForPractice.id);
            expect(startSpy).to.have.been.called;
            expect(randomizeStub).to.have.been.called;
        });

        it('should submit quiz', () => {
            const serviceStub = sinon.stub(exerciseService, 'findForStudent').returns(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
            fixture.detectChanges();

            const submitButton = fixture.debugElement.nativeElement.querySelector('#submit-quiz');
            expect(submitButton).to.exist;

            submitButton.click();
            fixture.detectChanges();

            const request = httpMock.expectOne({ method: 'POST' });
            const quizSubmission = { submissionDate: now, submitted: true } as QuizSubmission;
            request.flush({ submission: quizSubmission, participation: { exercise: quizExerciseForPractice } as StudentParticipation } as Result);
            expect(request.request.url).to.equal(`api/exercises/${quizExerciseForPractice.id}/submissions/practice`);
            fixture.detectChanges();

            expect(serviceStub).to.have.been.calledWith(quizExerciseForPractice.id);
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
                    resultForSolutionServiceStub = sinon.stub(exerciseService, 'find').returns(of({ body: quizExerciseForPractice } as HttpResponse<QuizExercise>));
                });
        });

        afterEach(function () {
            sinon.restore();
        });

        it('should initialize', () => {
            fixture.detectChanges();
            expect(component).to.be.ok;
        });

        it('should initialize and show solution', () => {
            fixture.detectChanges();

            expect(resultForSolutionServiceStub).to.have.been.calledWith(quizExerciseForPractice.id);
            expect(component.showingResult).to.be.true;
            expect(component.totalScore).to.equal(6);
        });

        it('should update time', () => {
            fixture.detectChanges();

            // Test the error branches first
            component.quizExercise = {} as QuizExercise;
            fixture.detectChanges();

            component.updateDisplayedTimes();
            fixture.detectChanges();

            expect(component.remainingTimeSeconds).to.equal(0);
            expect(component.remainingTimeText).to.equal('?');
            expect(component.timeUntilStart).to.equal('');

            // Now test the remaining non-error branches
            component.quizExercise = quizExerciseUnreleased;
            component.updateDisplayedTimes();
            fixture.detectChanges();

            component.quizExercise = quizExerciseForResults;
            component.submission = { submissionDate: now, submitted: true } as QuizSubmission;
            component.updateDisplayedTimes();
            fixture.detectChanges();

            expect(component.remainingTimeText).to.equal('showStatistic.quizhasEnded');
            expect(component.timeUntilStart).to.equal('showStatistic.now');
        });
    });
});
