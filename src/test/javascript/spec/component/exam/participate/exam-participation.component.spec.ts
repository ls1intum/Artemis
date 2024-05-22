import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
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
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ExamResultSummaryComponent } from 'app/exam/participate/summary/exam-result-summary.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { By } from '@angular/platform-browser';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { MockLocalStorageService } from '../../../helpers/mocks/service/mock-local-storage.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { LocalStorageService } from 'ngx-webstorage';
import { ExamLiveEvent, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { MockExamParticipationLiveEventsService } from '../../../helpers/mocks/service/mock-exam-participation-live-events.service';
import { ExamPage } from 'app/entities/exam-page.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { FormsModule } from '@angular/forms';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';

describe('ExamParticipationComponent', () => {
    const course = { id: 456 } as Course;
    const exam: Exam = new Exam();
    exam.course = course;
    exam.id = 123;
    exam.testExam = false;
    const studentExam: StudentExam = new StudentExam();
    studentExam.testRun = false;
    studentExam.id = 1;
    studentExam.exercises = [];

    let fixture: ComponentFixture<ExamParticipationComponent>;
    let comp: ExamParticipationComponent;
    let examParticipationService: ExamParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseExerciseService: CourseExerciseService;
    let textSubmissionService: TextSubmissionService;
    let modelingSubmissionService: ModelingSubmissionService;
    let alertService: AlertService;
    let artemisServerDateService: ArtemisServerDateService;
    let examParticipationLiveEventsService: ExamParticipationLiveEventsService;
    let translateService: TranslateService;
    let examManagementService: ExamManagementService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                MockComponent(ExamExerciseOverviewPageComponent),
                ExamParticipationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamNavigationBarComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockComponent(JhiConnectionStatusComponent),
                MockDirective(TranslateDirective),
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamResultSummaryComponent),
                MockComponent(ExamLiveEventsButtonComponent),
                MockComponent(ExamTimerComponent),
                MockPipe(ArtemisDatePipe),
            ],
            imports: [ArtemisTestModule, FormsModule],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService },
                MockProvider(ExamParticipationService),
                MockProvider(ModelingSubmissionService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(TextSubmissionService),
                MockProvider(FileUploadSubmissionService),
                MockProvider(ArtemisServerDateService),
                MockProvider(TranslateService),
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
                MockProvider(ArtemisDatePipe),
                MockProvider(ExamManagementService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationComponent);
                comp = fixture.componentInstance;
                examParticipationService = TestBed.inject(ExamParticipationService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                courseExerciseService = TestBed.inject(CourseExerciseService);
                textSubmissionService = TestBed.inject(TextSubmissionService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                alertService = TestBed.inject(AlertService);
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
                examParticipationLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
                translateService = TestBed.inject(TranslateService);
                examManagementService = TestBed.inject(ExamManagementService);
                comp.handInEarly = false;
                comp.handInPossible = true;
                comp.testStartTime = undefined;
                comp.exam = exam;
                comp.studentExam = studentExam;
                comp.courseId = course.id!;
                comp.examId = exam.id!;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        comp.ngOnDestroy();
    });

    it('should initialize', () => {
        //jest.spyOn(examParticipationService,'examState$','get').mockReturnValue(of(initialStates));
        fixture.detectChanges();
        expect(ExamParticipationComponent).toBeTruthy();
    });

    it('should get end button enabled and disabled', () => {
        fixture.detectChanges();
        comp.testRun = true;
        comp.updateConfirmation();
        expect(comp.endButtonEnabled).toBeFalse();

        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        //comp.testRun = false;
        comp.enteredName = 'admin';
        comp.accountName = 'admin';
        comp.confirmed = true;
        comp.exam.visibleDate = dayjs().subtract(1, 'hours');

        comp.handInPossible = true;
        expect(comp.endButtonEnabled).toBeTrue();
    });

    it('should get whether student failed to submit', () => {
        comp.testRun = true;
        expect(comp.studentFailedToSubmit).toBeFalse();

        comp.testRun = false;
        const startDate = dayjs();
        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.studentExam = new StudentExam();
        comp.studentExam.exam = comp.exam;
        comp.exam.startDate = startDate.subtract(2, 'hours');
        comp.exam.testExam = false;
        comp.studentExam.workingTime = 3600;
        comp.exam.gracePeriod = 1;
        comp.studentExam.submitted = false;
        expect(comp.studentFailedToSubmit).toBeTrue();
    });

    it('should not display timer when exam was not submitted', () => {
        comp.exam = new Exam();
        comp.studentExam = new StudentExam();
        comp.studentExam.exercises = [];
        jest.spyOn(comp, 'studentFailedToSubmit', 'get').mockReturnValue(true);
        jest.spyOn(comp, 'isGracePeriodOver').mockReturnValue(true);
        fixture.detectChanges();

        const examTimerDebugElement = fixture.debugElement.query(By.directive(ExamTimerComponent));
        expect(examTimerDebugElement).toBeFalsy();
    });

    it('should display timer during working time', () => {
        comp.exam = new Exam();
        comp.studentExam = new StudentExam();
        comp.studentExam.exercises = [];
        jest.spyOn(comp, 'studentFailedToSubmit', 'get').mockReturnValue(false);
        jest.spyOn(comp, 'isGracePeriodOver').mockReturnValue(true);
        fixture.detectChanges();

        const examTimerDebugElement = fixture.debugElement.query(By.directive(ExamTimerComponent));
        expect(examTimerDebugElement).toBeTruthy();
    });

    describe('ExamParticipationSummaryComponent for TestRuns', () => {
        it('should initialize and display test run ribbon', () => {
            comp.testRunId = 3;
            fixture.detectChanges();
            expect(fixture).toBeTruthy();
            expect(!!comp.testRunId).toBeTrue();
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).toBeDefined();
        });
        it('should initialize and not display test run ribbon', () => {
            comp.exam.id = 2;
            comp.ngOnInit();
            fixture.detectChanges();
            expect(fixture).toBeTruthy();
            expect(!!comp.testRunId).toBeFalse();
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).toBeNull();
        });
    });

    describe('isProgrammingExercise', () => {
        it('should return true if active exercise is a programming exercise', () => {
            comp.activeExamPage.exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExercise()).toBeTrue();
        });
        it('should return false if active exercise is not a programming exercise', () => {
            comp.activeExamPage.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            expect(comp.isProgrammingExercise()).toBeFalse();
        });
    });

    describe('isProgrammingExerciseWithCodeEditor', () => {
        it('should return true if programming exercise is with code editor', () => {
            comp.activeExamPage.exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithCodeEditor()).toBeFalse();
            (comp.activeExamPage.exercise as ProgrammingExercise).allowOnlineEditor = true;
            expect(comp.isProgrammingExerciseWithCodeEditor()).toBeTrue();
        });
    });

    describe('isProgrammingExerciseWithOfflineIDE', () => {
        it('should return true if active exercise is with offline ide', () => {
            comp.activeExamPage.exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithOfflineIDE()).toBeTrue();
            (comp.activeExamPage.exercise as ProgrammingExercise).allowOfflineIde = false;
            expect(comp.isProgrammingExerciseWithOfflineIDE()).toBeFalse();
        });
    });

    it('should redirect to exam summary after test run is over', () => {
        comp.courseId = 1;
        comp.examId = 2;
        comp.testRunId = 3;
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const submitStudentExamSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        comp.ngOnInit();
        expect(comp.studentExam).toEqual(studentExam);
        comp.onExamEndConfirmed();
        expect(submitStudentExamSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'exams', 2, 'test-runs', 3, 'summary']);
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
                if (type == 'modeling') {
                    submission = new ModelingSubmission();
                }
                studentParticipation.submissions = [submission];
            } else {
                studentParticipation.submissions = [];
            }
            exercise.studentParticipations = [studentParticipation];
            return exercise;
        };
        const latestPendingSubmissionSpy = (programmingSubmissionService.getLatestPendingSubmissionByParticipationId = jest.fn().mockReturnValue(
            of({
                submission: new ProgrammingSubmission(),
                participationId: 2,
                submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            } as ProgrammingSubmissionStateObj),
        ));

        const firstExercise = exerciseWithParticipation('programming', false);
        const secondExercise = exerciseWithParticipation('modeling', true);

        // Create submission for programming exercises without submission
        const firstParticipation = firstExercise.studentParticipations![0];
        firstParticipation.id = 2;

        studentExam.exercises = [firstExercise, secondExercise];
        comp.examStarted(studentExam);
        expect(firstParticipation.submissions).toBeDefined();
        expect(firstParticipation.submissions!.length).toBeGreaterThan(0);
        expect(latestPendingSubmissionSpy).toHaveBeenCalledOnce();
        expect(firstExercise.studentParticipations![0].submissions![0].submitted).toBeTrue();

        // Sync exercises with submission
        const secondSubmission = secondExercise.studentParticipations![0].submissions![0];
        expect(secondSubmission.isSynced).toBeTrue();
        expect(secondSubmission.submitted).toBeFalse();

        if (studentExam.testRun || studentExam.exam?.testExam) {
            expect(comp.individualStudentEndDate).toEqual(comp.testStartTime!.add(studentExam.workingTime!, 'seconds'));
        } else {
            expect(comp.individualStudentEndDate).toEqual(comp.exam.startDate!.add(studentExam.workingTime!, 'seconds'));
        }

        // Initialize Exam Overview Page
        expect(comp.activeExamPage.exercise).toBeUndefined();
        expect(comp.activeExamPage.isOverviewPage).toBeTrue();
    };

    it('should initialize exercises when exam starts', () => {
        const studentExam = new StudentExam();
        studentExam.workingTime = 100;
        studentExam.testRun = true;
        comp.testRunId = 3;
        comp.testStartTime = dayjs().subtract(1000, 'seconds');
        comp.exam = new Exam();
        testExamStarted(studentExam);
    });

    it('should initialize test exam', () => {
        const studentExam = new StudentExam();
        const exam = new Exam();
        exam.testExam = true;
        studentExam.exam = exam;
        studentExam.workingTime = 100;
        comp.testStartTime = dayjs().subtract(1000, 'seconds');
        comp.exam = exam;
        comp.testExam = exam.testExam;
        testExamStarted(studentExam);
    });

    it('should initialize exercise without test run', () => {
        // Should calculate time from exam start date when no test run, rest does not get effected
        const startDate = dayjs();
        comp.exam = new Exam();
        comp.exam.startDate = dayjs(startDate);
        const workingTime = 1000;
        const studentExam = new StudentExam();
        studentExam.workingTime = workingTime;
        testExamStarted(studentExam);
        expect(comp.individualStudentEndDate).toEqual(startDate.add(workingTime, 'seconds'));
    });

    it('should create participation for given exercise', () => {
        comp.exam = new Exam();
        comp.exam.course = new Course();
        const createdParticipation = new StudentParticipation();
        const programmingSubmission = new ProgrammingSubmission();
        createdParticipation.submissions = [programmingSubmission];
        createdParticipation.exercise = new ProgrammingExercise(new Course(), undefined);
        const courseExerciseServiceStub = jest.spyOn(courseExerciseService, 'startExercise').mockReturnValue(of(createdParticipation));
        const exercise = new ProgrammingExercise(new Course(), undefined);
        let index = 0;
        const states = ['generating', 'success'];
        comp.generateParticipationStatus.subscribe((state) => {
            expect(state).toEqual(states[index]);
            index++;
        });

        comp.createParticipationForExercise(exercise).subscribe((participation) => {
            expect(createdParticipation.exercise).toBeUndefined();
            expect(programmingSubmission.isSynced).toBeTrue();
            expect(participation).toEqual(createdParticipation);
        });
        expect(courseExerciseServiceStub).toHaveBeenCalledOnce();
    });

    it('should generate participation state when participation creation fails', () => {
        comp.exam = new Exam();
        comp.exam.course = new Course();
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const courseExerciseServiceStub = jest.spyOn(courseExerciseService, 'startExercise').mockReturnValue(throwError(() => httpError));
        let index = 0;
        const states = ['generating', 'failed'];
        comp.generateParticipationStatus.subscribe((state) => {
            expect(state).toEqual(states[index]);
            index++;
        });
        const exercise = new ProgrammingExercise(new Course(), undefined);
        comp.createParticipationForExercise(exercise);
        expect(courseExerciseServiceStub).toHaveBeenCalledOnce();
    });

    describe('websocket working time subscription', () => {
        const startDate = dayjs('2022-02-21T23:00:00+01:00');

        beforeEach(() => {
            comp.studentExam = { id: 3, workingTime: 420, numberOfExamSessions: 0 };
            comp.studentExamId = comp.studentExam.id!;
            examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        });

        it('should correctly increase working time', () => {
            const event = {
                newWorkingTime: 1337,
            } as any as ExamLiveEvent;
            jest.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = jest.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam.workingTime).toBe(1337);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });

        it('should correctly increase working time to next day', () => {
            const event = {
                newWorkingTime: 9001,
            } as any as ExamLiveEvent;
            jest.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = jest.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            // the following line uses the current time zone and therefore avoids a time zone flaky test
            // (if left out, the test would pass in the German time zone and fail in most other time zones)
            const startDate = dayjs().set('h', 23); //today at 23:00
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam.workingTime).toBe(9001);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });

        it('should correctly decrease working time', () => {
            const event = {
                newWorkingTime: 42,
            } as any as ExamLiveEvent;
            jest.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = jest.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam.workingTime).toBe(42);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });
    });

    describe('trigger save', () => {
        let textSubmissionUpdateSpy: jest.SpyInstance;
        let modelingSubmissionUpdateSpy: jest.SpyInstance;
        let quizSubmissionUpdateSpy: jest.SpyInstance;

        beforeEach(() => {
            comp.studentExam = new StudentExam();
            comp.studentExam.exercises = [];
            comp.exam = new Exam();
            fixture.detectChanges();
        });

        const expectSyncedSubmissions = (submission: Submission, syncedSubmission: Submission) => {
            expect(submission.isSynced).toBeTrue();
            expect(submission.submitted).toBeTrue();
            expect(syncedSubmission.isSynced).toBeTrue();
            expect(syncedSubmission.submitted).toBeFalse();
        };

        it('should sync text submissions', () => {
            const textExercise = new TextExercise(new Course(), undefined);
            textExercise.id = 5;
            const participation = new StudentParticipation();
            const submission = new TextSubmission();
            const syncedSubmission = new TextSubmission();
            syncedSubmission.isSynced = true;
            participation.submissions = [submission, syncedSubmission];
            participation.submissions = [submission, syncedSubmission];
            textExercise.studentParticipations = [participation];
            comp.studentExam.exercises = [textExercise];
            textSubmissionUpdateSpy = jest.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false);
            expect(textSubmissionUpdateSpy).toHaveBeenCalledWith(submission, 5);
            expect(textSubmissionUpdateSpy).not.toHaveBeenCalledWith(syncedSubmission, 5);
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
            modelingSubmissionUpdateSpy = jest.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false);
            expect(modelingSubmissionUpdateSpy).toHaveBeenCalledWith(submission, 5);
            expect(modelingSubmissionUpdateSpy).not.toHaveBeenCalledWith(syncedSubmission, 5);
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
            quizSubmissionUpdateSpy = jest.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(submission));
            comp.triggerSave(false);
            tick(500);
            expect(quizSubmissionUpdateSpy).toHaveBeenCalledWith(5, submission);
            expect(quizSubmissionUpdateSpy).not.toHaveBeenCalledWith(5, syncedSubmission);
            expectSyncedSubmissions(submission, syncedSubmission);
        }));
    });

    it('should submit exam when end confirmed', () => {
        comp.studentExam = new StudentExam();
        comp.studentExam.submitted = false;
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        comp.exam = new Exam();
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam?.submitted).toBeTrue();
    });

    it('should show error when already submitted for test run and successfully loading student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const studentExam = new StudentExam();
        const loadTestRunWithExercisesForConductionSpy = jest.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.testRunId = 3;
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(loadTestRunWithExercisesForConductionSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).not.toHaveBeenCalled();
        expect(comp.studentExam).toEqual(studentExam);
    });

    it('should show error when already submitted for test run and failed to load student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const loadTestRunWithExercisesForConductionSpy = jest
            .spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction')
            .mockReturnValue(throwError(() => new Error()));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.testRunId = 3;
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(loadTestRunWithExercisesForConductionSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when already submitted and successfully loading student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const studentExam = new StudentExam();
        const getOwnStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.testRunId = 0;
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(getOwnStudentExamSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).not.toHaveBeenCalled();
        expect(comp.studentExam).toEqual(studentExam);
    });

    it('should show error when already submitted and failed to load student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const getOwnStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => new Error()));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.testRunId = 0;
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(getOwnStudentExamSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when not submitted', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    describe('canDeactivate', () => {
        it('should return true if logout is true', () => {
            comp.loggedOut = true;
            expect(comp.canDeactivate()).toBeTrue();
        });

        it('should call translateService', () => {
            const translateServiceSpy = jest.spyOn(translateService, 'instant');
            comp.canDeactivateWarning;
            expect(translateServiceSpy).toHaveBeenCalledOnce();
        });
    });

    describe('unloadNotification', () => {
        it('should set event return value', () => {
            jest.spyOn(comp, 'canDeactivate').mockReturnValue(false);
            jest.spyOn(comp, 'canDeactivateWarning', 'get').mockReturnValue('warning');
            const event = { returnValue: undefined };
            comp.unloadNotification(event);
            expect(event.returnValue).toBe('warning');
        });
    });

    describe('isOver', () => {
        it('should return true if exam has ended', () => {
            const studentExam = new StudentExam();
            studentExam.ended = true;
            comp.studentExam = studentExam;
            expect(comp.isOver()).toBeTrue();
        });
        it('should return true when handed in early', () => {
            comp.handInEarly = true;
            expect(comp.isOver()).toBeTrue();
        });
        it('should return true if student exam has been submitted', () => {
            const studentExam = new StudentExam();
            studentExam.submitted = true;
            comp.studentExam = studentExam;
            expect(comp.isOver()).toBeTrue();
        });
        it('should be over if individual end date is before server date', () => {
            const endDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.individualStudentEndDate = endDate;
            comp.studentExam.submitted = false;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isOver()).toBeTrue();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
        it('should not be over if individual end date is after server date', () => {
            const endDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.individualStudentEndDate = endDate;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isOver()).toBeFalse();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    const setComponentWithoutTestRun = () => {
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
        comp.exam = new Exam();
    };

    describe('isVisible', () => {
        it('should be visible if test run', () => {
            comp.exam.visibleDate = undefined;
            comp.testRunId = 3;
            expect(comp.isVisible()).toBeTrue();
            comp.testRunId = undefined;
            expect(comp.isVisible()).toBeFalse();
        });

        it('should be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.exam.visibleDate = visibleDate;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isVisible()).toBeTrue();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.exam.visibleDate = visibleDate;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isVisible()).toBeFalse();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    describe('isActive', () => {
        it('should be active if test run', () => {
            comp.exam.startDate = undefined;
            comp.testRunId = 3;
            expect(comp.isActive()).toBeTrue();
            comp.testRunId = undefined;
            expect(comp.isActive()).toBeFalse();
        });

        it('should be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.exam.startDate = startDate;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isActive()).toBeTrue();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.exam.startDate = startDate;
            const serverNowSpy = jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isActive()).toBeFalse();
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    it('should clear autoSaveInterval when exam ended', () => {
        const clearIntervalSpy = jest.spyOn(window, 'clearInterval');
        comp.autoSaveInterval = 1;
        comp.studentExam = new StudentExam();
        comp.studentExam.exercises = [];
        comp.exam = new Exam();
        fixture.detectChanges();
        comp.examEnded();
        expect(clearIntervalSpy).toHaveBeenCalledWith(comp.autoSaveInterval);
    });

    describe('onPageChange', () => {
        it('should trigger save and initialize exercise when exercise changed', () => {
            comp.exerciseIndex = 0;
            const exercise1 = new TextExercise(new Course(), undefined);
            exercise1.id = 15;
            const exercise2 = new ProgrammingExercise(new Course(), undefined);
            exercise2.id = 42;
            comp.studentExam = new StudentExam();
            comp.studentExam.exercises = [exercise1, exercise2];
            fixture.detectChanges();
            const triggerSpy = jest.spyOn(comp, 'triggerSave');
            const exerciseChange = { overViewChange: false, exercise: exercise2, forceSave: true };
            const createParticipationForExerciseSpy = jest.spyOn(comp, 'createParticipationForExercise').mockReturnValue(of(new StudentParticipation()));
            comp.exam = new Exam();
            comp.onPageChange(exerciseChange);
            expect(triggerSpy).toHaveBeenCalledWith(true);
            expect(comp.exerciseIndex).toBe(1);
            expect(createParticipationForExerciseSpy).toHaveBeenCalledWith(exercise2);
        });

        it('should trigger save and initialize exercise when exercise changed and participation is valid', () => {
            const exercise = new QuizExercise(new Course(), undefined);
            exercise.id = 42;
            const participation = new StudentParticipation();
            participation.initializationState = InitializationState.INITIALIZED;
            const submission = new QuizSubmission();
            participation.submissions = [submission];
            exercise.studentParticipations = [participation];
            const triggerSpy = jest.spyOn(comp, 'triggerSave');
            const exerciseChange = { overViewChange: false, exercise: exercise, forceSave: true };
            comp.exam = new Exam();
            comp.activeExamPage = new ExamPage();
            comp.activeExamPage.exercise = exercise;
            comp.studentExam = new StudentExam();
            comp.studentExam.exercises = [exercise];
            comp.pageComponentVisited = [true];
            comp.examStartConfirmed = true;
            fixture.detectChanges();
            comp.onPageChange(exerciseChange);

            expect(triggerSpy).toHaveBeenCalledWith(true);
            expect(comp.exerciseIndex).toBe(0);
        });
    });

    describe('handleHandInEarly', () => {
        it('should reset pageComponentVisited after the hand-in-early window is closed', () => {
            // Create exercises
            const exercise1 = new ProgrammingExercise(new Course(), undefined);
            exercise1.id = 15;
            const exercise2 = new ProgrammingExercise(new Course(), undefined);
            exercise2.id = 42;
            exercise2.allowOnlineEditor = true;
            exercise2.allowOfflineIde = false;
            const exercise3 = new ProgrammingExercise(new Course(), undefined);
            exercise3.id = 16;
            exercise3.allowOnlineEditor = false;
            exercise3.allowOfflineIde = true;

            // Set initial component state
            comp.handInEarly = true;
            comp.studentExam = new StudentExam();
            comp.studentExam.exercises = [exercise1, exercise2, exercise3];
            comp.activeExamPage = {
                isOverviewPage: false,
                exercise: exercise2,
            };
            comp.exerciseIndex = 1;
            comp.pageComponentVisited = [true, true, true];

            // Spy on the private method resetPageComponentVisited
            const resetPageComponentVisitedSpy = jest.spyOn<any, any>(comp, 'resetPageComponentVisited');

            // Call toggleHandInEarly to change the handInEarly state
            comp.handleHandInEarly();

            // Verify that resetPageComponentVisited has been called with the correct index
            expect(resetPageComponentVisitedSpy).toHaveBeenCalledExactlyOnceWith(1);

            // Verify that the pageComponentVisited array and exerciseIndex are updated correctly
            expect(comp.pageComponentVisited).toEqual([false, true, false]);
            expect(comp.exerciseIndex).toBe(1);
        });

        it('should trigger save', () => {
            const triggerSaveSpy = jest.spyOn(comp, 'triggerSave').mockImplementation(() => {});
            fixture.detectChanges();
            comp.handInEarly = false;
            comp.handleHandInEarly();

            expect(triggerSaveSpy).toHaveBeenCalledOnce();
        });
    });

    describe('toggleHandInEarly', () => {
        it('should not fetch attendance check status if exam is a test exam', () => {
            comp.exam = new Exam();
            comp.exam.testExam = true;
            fixture.detectChanges();
            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = jest.spyOn<any, any>(examManagementService, 'isAttendanceChecked');

            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should not fetch attendance check status if exam is not an exam with attendance check', () => {
            comp.exam = new Exam();
            comp.exam.examWithAttendanceCheck = false;
            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = jest.spyOn<any, any>(examManagementService, 'isAttendanceChecked');
            fixture.detectChanges();
            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should not fetch attendance check status if user clicks continue', () => {
            comp.handInEarly = true;

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = jest.spyOn<any, any>(examManagementService, 'isAttendanceChecked');
            fixture.detectChanges();
            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should fetch attendance check status if exam is an exam with attendance check', () => {
            comp.exam = new Exam();
            comp.exam.examWithAttendanceCheck = true;

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = jest.spyOn<any, any>(examManagementService, 'isAttendanceChecked').mockReturnValue(of(new HttpResponse({ body: true })));
            fixture.detectChanges();
            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has been called
            expect(attendanceCheckSpy).toHaveBeenCalledOnce();
            expect(comp.attendanceChecked).toBeTrue();
        });
    });

    describe('activePageIndex', () => {
        it('should return -1 if active page is overview page', () => {
            comp.activeExamPage = new ExamPage();
            comp.activeExamPage.isOverviewPage = true;
            expect(comp.activePageIndex).toBe(-1);
        });

        it('should return the index of the active page', () => {
            const exercise0 = new QuizExercise(undefined, undefined);
            exercise0.id = 5;
            const exercise1 = new ProgrammingExercise(undefined, undefined);
            exercise1.id = 6;

            comp.activeExamPage = new ExamPage();
            comp.activeExamPage.exercise = exercise1;

            comp.studentExam = new StudentExam();
            comp.studentExam.exercises = [exercise0, exercise1];

            expect(comp.activePageIndex).toBe(1);
        });
    });
});
