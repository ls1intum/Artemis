import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Submission } from 'app/entities/submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamParticipationCoverComponent } from 'app/exam/participate/exam-cover/exam-participation-cover.component';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { AlertComponent } from 'app/shared/alert/alert.component.ts';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { TranslatePipeMock } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

describe('ExamParticipationComponent', () => {
    let fixture: ComponentFixture<ExamParticipationComponent>;
    let comp: ExamParticipationComponent;
    let examParticipationService: ExamParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseExerciseService: CourseExerciseService;
    let textSubmissionService: TextSubmissionService;
    let modelingSubmissionService: ModelingSubmissionService;
    let alertService: JhiAlertService;
    let artemisServerDateService: ArtemisServerDateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ExamParticipationComponent,
                TranslatePipeMock,
                MockDirective(JhiTranslateDirective),
                MockComponent(ExamParticipationCoverComponent),
                MockComponent(ExamNavigationBarComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockComponent(JhiConnectionStatusComponent),
                MockComponent(AlertComponent),
                TestRunRibbonComponent,
                MockComponent(ExamParticipationSummaryComponent),
            ],
            providers: [
                MockProvider(JhiWebsocketService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: '1', examId: '2', testRunId: '3' }),
                    },
                },
                MockProvider(ExamParticipationService),
                MockProvider(ModelingSubmissionService),
                ProgrammingSubmissionService,
                MockProvider(TextSubmissionService),
                MockProvider(FileUploadSubmissionService),
                MockProvider(ArtemisServerDateService),
                MockProvider(TranslateService),
                MockProvider(JhiAlertService),
                MockProvider(CourseExerciseService),
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationComponent);
                comp = fixture.componentInstance;
                examParticipationService = TestBed.inject(ExamParticipationService);
                programmingSubmissionService = fixture.debugElement.injector.get(ProgrammingSubmissionService);
                courseExerciseService = TestBed.inject(CourseExerciseService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                alertService = fixture.debugElement.injector.get(JhiAlertService);
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
                fixture.detectChanges();
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ExamParticipationComponent).to.be.ok;
    });

    describe('ExamParticipationSummaryComponent for TestRuns', () => {
        it('should initialize and display test run ribbon', function () {
            fixture.detectChanges();
            expect(fixture).to.be.ok;
            expect(!!comp.testRunId).to.be.true;
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).to.exist;
        });
        it('should initialize and not display test run ribbon', function () {
            TestBed.get(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
            comp.ngOnInit();
            fixture.detectChanges();
            expect(fixture).to.be.ok;
            expect(!!comp.testRunId).to.be.false;
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).to.not.exist;
        });
    });

    describe('isProgrammingExercise', () => {
        it('should return true if active exercise is a programming exercise', () => {
            comp.activeExercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExercise()).to.equal(true);
        });
        it('should return false if active exercise is not a programming exercise', () => {
            comp.activeExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            expect(comp.isProgrammingExercise()).to.equal(false);
        });
    });

    describe('isProgrammingExerciseWithCodeEditor', () => {
        it('should return true if programming exercise is with code editor', () => {
            comp.activeExercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithCodeEditor()).to.equal(false);
            (comp.activeExercise as ProgrammingExercise).allowOnlineEditor = true;
            expect(comp.isProgrammingExerciseWithCodeEditor()).to.equal(true);
        });
    });

    describe('isProgrammingExerciseWithOfflineIDE', () => {
        it('should return true if active exercise is with offline ide', () => {
            comp.activeExercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithOfflineIDE()).to.equal(true);
            (comp.activeExercise as ProgrammingExercise).allowOfflineIde = false;
            expect(comp.isProgrammingExerciseWithOfflineIDE()).to.equal(false);
        });
    });

    it('should load test run if test run id is defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        const loadTestRunStub = stub(examParticipationService, 'loadTestRunWithExercisesForConduction').returns(of(studentExam));
        comp.ngOnInit();
        expect(loadTestRunStub).to.have.been.called;
        expect(comp.studentExam).to.deep.equal(studentExam);
        expect(comp.exam).to.deep.equal(studentExam.exam);
    });
    it('should load exam if test run id is not defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.startDate = moment().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        const studentExamWithExercises = new StudentExam();
        TestBed.get(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamStub = stub(examParticipationService, 'loadStudentExam').returns(of(studentExam));
        const loadStudentExamWithExercisesForSummary = stub(examParticipationService, 'loadStudentExamWithExercisesForSummary').returns(of(studentExamWithExercises));
        comp.ngOnInit();
        expect(loadStudentExamStub).to.have.been.called;
        expect(comp.studentExam).to.deep.equal(studentExam);
        expect(comp.exam).to.deep.equal(studentExam.exam);
        expect(loadStudentExamWithExercisesForSummary).to.not.have.been.called;
        studentExam.exam.course = new Course();
        studentExam.ended = true;
        studentExam.submitted = true;
        comp.ngOnInit();
        expect(loadStudentExamStub).to.have.been.called;
        expect(loadStudentExamWithExercisesForSummary).to.have.been.called;
        expect(comp.studentExam).to.deep.equal(studentExamWithExercises);
        expect(comp.studentExam).to.not.deep.equal(studentExam);
    });
    it('should load exam from local storage if needed', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.id = 1;
        const loadStudentExamStub = stub(examParticipationService, 'loadStudentExam').returns(of(studentExam));

        const localStudentExam = new StudentExam();
        localStudentExam.exam = studentExam.exam;
        localStudentExam.id = 2; // use a different id for testing purposes only
        const lastSaveFailedStub = stub(examParticipationService, 'lastSaveFailed').returns(true);
        const loadLocalStudentExamStub = stub(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage').returns(of(localStudentExam));

        TestBed.get(ActivatedRoute).params = of({ courseId: '1', examId: '2' });

        comp.ngOnInit();

        expect(loadStudentExamStub).to.have.been.called;
        expect(lastSaveFailedStub).to.have.been.called;
        expect(loadLocalStudentExamStub).to.have.been.called;
        expect(comp.studentExam).to.deep.equal(localStudentExam);
        expect(comp.studentExam).to.not.deep.equal(studentExam);
        expect(comp.exam).to.deep.equal(studentExam.exam);
    });

    const testExamStarted = (studentExam: StudentExam) => {
        const exerciseWithParticipation = (type: 'programming' | 'modeling', withSubmission: boolean) => {
            let exercise = new ProgrammingExercise(new Course(), undefined);
            if (type === 'modeling') {
                exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            }
            const studentParticipation = new StudentParticipation();
            if (withSubmission) {
                let submission = new ProgrammingSubmission();
                if ('modeling') {
                    submission = new ModelingSubmission();
                }
                studentParticipation.submissions = [submission];
            } else {
                studentParticipation.submissions = [];
            }
            exercise.studentParticipations = [studentParticipation];
            return exercise;
        };
        const latestPendingSubmissionStub = stub(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId').returns(
            of({
                submission: new ProgrammingSubmission(),
                participationId: 2,
                submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            } as ProgrammingSubmissionStateObj),
        );
        const createParticipationForExerciseStub = stub(comp, 'createParticipationForExercise').returns(of(new StudentParticipation()));
        comp.ngOnInit();
        const firstExercise = exerciseWithParticipation('programming', false);
        const secondExercise = exerciseWithParticipation('modeling', true);

        // Create submission for programming exercises without submission
        const firstParticipation = firstExercise.studentParticipations![0];
        firstParticipation.id = 2;

        studentExam.exercises = [firstExercise, secondExercise];
        comp.examStarted(studentExam);
        expect(firstParticipation.submissions).to.exist;
        expect(firstParticipation.submissions!.length).to.greaterThan(0);
        expect(latestPendingSubmissionStub).to.have.been.called;
        expect(firstExercise.studentParticipations![0].submissions![0].submitted).to.equal(true);

        // Sync exercises with submission
        const secondSubmission = secondExercise.studentParticipations![0].submissions![0];
        expect(secondSubmission.isSynced).to.equal(true);
        expect(secondSubmission.submitted).to.equal(false);

        // Initialize exercise
        expect(comp.activeExercise).to.deep.equal(firstExercise);
        expect(createParticipationForExerciseStub).to.have.been.calledWith(firstExercise);
    };

    it('should initialize exercises when exam starts', () => {
        const studentExam = new StudentExam();
        studentExam.workingTime = 100;
        comp.testRunStartTime = moment().subtract(1000, 'seconds');
        testExamStarted(studentExam);
    });

    it('should initialize exercise without test run', () => {
        // Should calculate time from exam start date when no test run, rest does not get effected
        TestBed.get(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
        const startDate = moment();
        comp.exam = new Exam();
        comp.exam.startDate = moment(startDate);
        const workingTime = 1000;
        const studentExam = new StudentExam();
        studentExam.workingTime = workingTime;
        testExamStarted(studentExam);
        expect(comp.individualStudentEndDate).to.deep.equal(startDate.add(workingTime, 'seconds'));
    });

    it('should create participation for given exercise', () => {
        comp.exam = new Exam();
        comp.exam.course = new Course();
        const createdParticipation = new StudentParticipation();
        const programmingSubmission = new ProgrammingSubmission();
        createdParticipation.submissions = [programmingSubmission];
        createdParticipation.exercise = new ProgrammingExercise(new Course(), undefined);
        const courseExerciseServiceStub = stub(courseExerciseService, 'startExercise').returns(of(createdParticipation));
        const exercise = new ProgrammingExercise(new Course(), undefined);
        let index = 0;
        const states = ['generating', 'success'];
        comp.generateParticipationStatus.subscribe((state) => {
            expect(state).to.equal(states[index]);
            index++;
        });

        comp.createParticipationForExercise(exercise).subscribe((participation) => {
            expect(createdParticipation.exercise).to.not.exist;
            expect(programmingSubmission.isSynced).to.equal(true);
            expect(participation).to.deep.equal(createdParticipation);
        });
        expect(courseExerciseServiceStub).to.have.been.called;
    });

    it('should generate participation state when participation creation fails', () => {
        comp.exam = new Exam();
        comp.exam.course = new Course();
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const courseExerciseServiceStub = stub(courseExerciseService, 'startExercise').returns(throwError(httpError));
        let index = 0;
        const states = ['generating', 'failed'];
        comp.generateParticipationStatus.subscribe((state) => {
            expect(state).to.equal(states[index]);
            index++;
        });
        const exercise = new ProgrammingExercise(new Course(), undefined);
        comp.createParticipationForExercise(exercise);
        expect(courseExerciseServiceStub).to.have.been.called;
    });

    describe('trigger save', () => {
        let textSubmissionUpdateStub: sinon.SinonStub;
        let modelingSubmissionUpdateStub: sinon.SinonStub;
        let quizSubmissionUpdateStub: sinon.SinonStub;

        beforeEach(() => {
            const studentExam = new StudentExam();
            comp.studentExam = studentExam;
        });

        afterEach(() => {
            sinon.restore();
        });

        const expectSyncedSubmissions = (submission: Submission, syncedSubmission: Submission) => {
            expect(submission.isSynced).to.equal(true);
            expect(submission.submitted).to.equal(true);
            expect(syncedSubmission.isSynced).to.equal(true);
            expect(syncedSubmission.submitted).to.equal(false);
        };

        it('should sync text submissions', () => {
            const textExercise = new TextExercise(new Course(), undefined);
            textExercise.id = 5;
            const participation = new StudentParticipation();
            const submission = new TextSubmission();
            const syncedSubmission = new TextSubmission();
            syncedSubmission.isSynced = true;
            participation.submissions = [submission, syncedSubmission];
            textExercise.studentParticipations = [participation];
            comp.studentExam.exercises = [textExercise];
            textSubmissionUpdateStub = stub(textSubmissionService, 'update').returns(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false);
            expect(textSubmissionUpdateStub).to.have.been.calledWithExactly(submission, 5);
            expect(textSubmissionUpdateStub).to.not.have.been.calledWithExactly(syncedSubmission, 5);
            expectSyncedSubmissions(submission, syncedSubmission);
        });

        it('should sync modeling submissions', () => {
            const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            modelingExercise.id = 5;
            const participation = new StudentParticipation();
            const submission = new ModelingSubmission();
            const syncedSubmission = new ModelingSubmission();
            syncedSubmission.isSynced = true;
            participation.submissions = [submission, syncedSubmission];
            modelingExercise.studentParticipations = [participation];
            comp.studentExam.exercises = [modelingExercise];
            modelingSubmissionUpdateStub = stub(modelingSubmissionService, 'update').returns(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false);
            expect(modelingSubmissionUpdateStub).to.have.been.calledWithExactly(submission, 5);
            expect(modelingSubmissionUpdateStub).to.not.have.been.calledWithExactly(syncedSubmission, 5);
            expectSyncedSubmissions(submission, syncedSubmission);
        });

        it('should sync quiz submissions', fakeAsync(() => {
            const quizExercise = new QuizExercise(new Course(), undefined);
            quizExercise.id = 5;
            const participation = new StudentParticipation();
            const submission = new QuizSubmission();
            const syncedSubmission = new QuizSubmission();
            syncedSubmission.isSynced = true;
            participation.submissions = [submission, syncedSubmission];
            quizExercise.studentParticipations = [participation];
            comp.studentExam.exercises = [quizExercise];
            quizSubmissionUpdateStub = stub(examParticipationService, 'updateQuizSubmission').returns(of(submission));
            comp.triggerSave(false);
            tick(500);
            expect(quizSubmissionUpdateStub).to.have.been.calledWithExactly(5, submission);
            expect(quizSubmissionUpdateStub).to.not.have.been.calledWithExactly(5, syncedSubmission);
            expectSyncedSubmissions(submission, syncedSubmission);
        }));
    });

    it('should submit exam when end confirmed', () => {
        const studentExam = new StudentExam();
        const submitStub = stub(examParticipationService, 'submitStudentExam').returns(of(studentExam));
        comp.onExamEndConfirmed();
        expect(submitStub).to.have.been.called;
        expect(comp.studentExam).to.deep.equal(studentExam);
    });

    it('should show error', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const submitStub = stub(examParticipationService, 'submitStudentExam').returns(throwError(httpError));
        const alertErrorStub = stub(alertService, 'error');
        comp.onExamEndConfirmed();
        expect(submitStub).to.have.been.called;
        expect(alertErrorStub).to.have.been.called;
    });

    describe('isOver', () => {
        afterEach(() => {
            sinon.restore();
        });

        it('should return true if exam has ended', () => {
            const studentExam = new StudentExam();
            studentExam.ended = true;
            comp.studentExam = studentExam;
            expect(comp.isOver()).to.equal(true);
        });
        it('should return true when handed in early', () => {
            comp.handInEarly = true;
            expect(comp.isOver()).to.equal(true);
        });
        it('should return true if student exam has been submitted', () => {
            const studentExam = new StudentExam();
            studentExam.submitted = true;
            comp.studentExam = studentExam;
            expect(comp.isOver()).to.equal(true);
        });
        it('should be over if individual end date is before server date', () => {
            const endDate = moment().subtract(1, 'days');
            const date = moment();
            comp.individualStudentEndDate = endDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isOver()).to.equal(true);
            expect(serverNowStub).to.have.been.called;
        });
        it('should not be over if individual end date is after server date', () => {
            const endDate = moment().add(1, 'days');
            const date = moment();
            comp.individualStudentEndDate = endDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isOver()).to.equal(false);
            expect(serverNowStub).to.have.been.called;
        });
    });

    const setComponentWithoutTestRun = () => {
        TestBed.get(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
        comp.exam = new Exam();
    };

    describe('isVisible', () => {
        afterEach(() => {
            sinon.restore();
        });

        it('should be visible if test run', () => {
            expect(comp.isVisible()).to.equal(true);
            setComponentWithoutTestRun();
            expect(comp.isVisible()).to.equal(false);
        });

        it('should be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = moment().subtract(1, 'days');
            const date = moment();
            comp.exam.visibleDate = visibleDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isVisible()).to.equal(true);
            expect(serverNowStub).to.have.been.called;
        });

        it('should not be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = moment().add(1, 'days');
            const date = moment();
            comp.exam.visibleDate = visibleDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isVisible()).to.equal(false);
            expect(serverNowStub).to.have.been.called;
        });
    });

    describe('isActive', () => {
        afterEach(() => {
            sinon.restore();
        });

        it('should be active if test run', () => {
            expect(comp.isActive()).to.equal(true);
            setComponentWithoutTestRun();
            expect(comp.isActive()).to.equal(false);
        });

        it('should be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = moment().subtract(1, 'days');
            const date = moment();
            comp.exam.startDate = startDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isActive()).to.equal(true);
            expect(serverNowStub).to.have.been.called;
        });

        it('should not be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = moment().add(1, 'days');
            const date = moment();
            comp.exam.startDate = startDate;
            const serverNowStub = stub(artemisServerDateService, 'now').returns(date);
            expect(comp.isActive()).to.equal(false);
            expect(serverNowStub).to.have.been.called;
        });
    });

    it('should clear autoSaveInterval when exam ended', () => {
        const clearIntervalSpy = sinon.spy(window, 'clearInterval');
        comp.autoSaveInterval = 1;
        comp.examEnded();
        expect(clearIntervalSpy).to.have.been.calledWith(comp.autoSaveInterval);
    });

    it('should trigger save and initialize exercise when exercise changed', () => {
        const exercise = new ProgrammingExercise(new Course(), undefined);
        const triggerStub = stub(comp, 'triggerSave');
        const exerciseChange = { exercise, forceSave: true };
        const createParticipationForExerciseStub = stub(comp, 'createParticipationForExercise').returns(of(new StudentParticipation()));
        comp.onExerciseChange(exerciseChange);
        expect(triggerStub).to.have.been.calledWith(true);
        expect(createParticipationForExerciseStub).to.have.been.calledWith(exercise);
    });
});
