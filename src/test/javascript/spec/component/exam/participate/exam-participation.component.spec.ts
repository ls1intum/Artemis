import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
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
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
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

describe('ExamParticipationComponent', () => {
    let fixture: ComponentFixture<ExamParticipationComponent>;
    let comp: ExamParticipationComponent;
    let examParticipationService: ExamParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseExerciseService: CourseExerciseService;
    let textSubmissionService: TextSubmissionService;
    let modelingSubmissionService: ModelingSubmissionService;
    let alertService: AlertService;
    let artemisServerDateService: ArtemisServerDateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                MockComponent(ExamExerciseOverviewPageComponent),
                ExamParticipationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamParticipationCoverComponent),
                MockComponent(ExamNavigationBarComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockComponent(JhiConnectionStatusComponent),
                MockDirective(TranslateDirective),
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamParticipationSummaryComponent),
            ],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ courseId: '1', examId: '2', testRunId: '3' }),
                    },
                },
                MockProvider(ExamParticipationService),
                MockProvider(ModelingSubmissionService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(TextSubmissionService),
                MockProvider(FileUploadSubmissionService),
                MockProvider(ArtemisServerDateService),
                MockProvider(TranslateService),
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
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
                fixture.detectChanges();
                comp.exam = new Exam();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        comp.ngOnDestroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ExamParticipationComponent).toBeTruthy();
    });

    describe('ExamParticipationSummaryComponent for TestRuns', () => {
        it('should initialize and display test run ribbon', () => {
            fixture.detectChanges();
            expect(fixture).toBeTruthy();
            expect(!!comp.testRunId).toBeTrue();
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).toBeDefined();
        });
        it('should initialize and not display test run ribbon', () => {
            TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
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

    it('should load test run if test run id is defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        const loadTestRunStub = jest.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        comp.ngOnInit();
        expect(loadTestRunStub).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExam);
        expect(comp.exam).toEqual(studentExam.exam);
    });

    it('should load exam if test run id is not defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        const studentExamWithExercises = { id: 1 };
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'loadStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExam);
        expect(comp.exam).toEqual(studentExam.exam);
        expect(loadStudentExamWithExercisesForSummary).not.toHaveBeenCalled();
        studentExam.exam.course = new Course();
        studentExam.ended = true;
        studentExam.submitted = true;
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(loadStudentExamWithExercisesForSummary).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExamWithExercises);
        expect(comp.studentExam).not.toEqual(studentExam);
    });

    it('should load new testExam if studentExam id is new', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: 'new' });
        const loadTestRunStub = jest.spyOn(examParticipationService, 'loadStudentExam').mockReturnValue(of(studentExam));
        comp.ngOnInit();
        expect(loadTestRunStub).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExam);
        expect(comp.exam).toEqual(studentExam.exam);
    });

    it('should load existing testExam if studentExam id is defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 150;
        studentExam.id = 4;
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.id = 4;
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '4' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'loadStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExam);
        expect(comp.exam).toEqual(studentExam.exam);
        expect(comp.studentExam.id).toEqual(studentExam.id);
        expect(loadStudentExamWithExercisesForSummary).not.toHaveBeenCalled();
    });

    it('should load existing testExam for summary if studentExam id is defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        studentExam.id = 3;
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.id = 3;
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '3' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'loadStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        studentExam.exam.course = new Course();
        studentExam.ended = true;
        studentExam.submitted = true;
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(loadStudentExamWithExercisesForSummary).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExamWithExercises);
        expect(comp.studentExam).not.toEqual(studentExam);
        expect(comp.studentExam.id).toEqual(studentExamWithExercises.id);
    });

    it('should load exam from local storage if needed', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.id = 1;
        const loadStudentExamStub = jest.spyOn(examParticipationService, 'loadStudentExam').mockReturnValue(of(studentExam));

        const localStudentExam = new StudentExam();
        localStudentExam.exam = studentExam.exam;
        localStudentExam.id = 2; // use a different id for testing purposes only
        const lastSaveFailedStub = jest.spyOn(examParticipationService, 'lastSaveFailed').mockReturnValue(true);
        const loadLocalStudentExamStub = jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage').mockReturnValue(of(localStudentExam));

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });

        comp.ngOnInit();

        expect(loadStudentExamStub).toHaveBeenCalledOnce();
        expect(lastSaveFailedStub).toHaveBeenCalledOnce();
        expect(loadLocalStudentExamStub).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(localStudentExam);
        expect(comp.studentExam).not.toEqual(studentExam);
        expect(comp.exam).toEqual(studentExam.exam);
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
        const latestPendingSubmissionSpy = (programmingSubmissionService.getLatestPendingSubmissionByParticipationId = jest.fn().mockReturnValue(
            of({
                submission: new ProgrammingSubmission(),
                participationId: 2,
                submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            } as ProgrammingSubmissionStateObj),
        ));
        comp.ngOnInit();
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

        // Initialize Exam Overview Page
        expect(comp.activeExamPage.exercise).toBeUndefined();
        expect(comp.activeExamPage.isOverviewPage).toBeTrue();
    };

    it('should initialize exercises when exam starts', () => {
        const studentExam = new StudentExam();
        studentExam.workingTime = 100;
        comp.testStartTime = dayjs().subtract(1000, 'seconds');
        comp.exam = new Exam();
        testExamStarted(studentExam);
    });

    it('should initialize exercise without test run', () => {
        // Should calculate time from exam start date when no test run, rest does not get effected
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
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

    describe('trigger save', () => {
        let textSubmissionUpdateSpy: jest.SpyInstance;
        let modelingSubmissionUpdateSpy: jest.SpyInstance;
        let quizSubmissionUpdateSpy: jest.SpyInstance;

        beforeEach(() => {
            comp.studentExam = new StudentExam();
            comp.exam = new Exam();
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
            textExercise.studentParticipations = [participation];
            comp.studentExam.exercises = [textExercise];
            textSubmissionUpdateSpy = jest.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false, false);
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
            comp.triggerSave(false, false);
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
            comp.triggerSave(false, false);
            tick(500);
            expect(quizSubmissionUpdateSpy).toHaveBeenCalledWith(5, submission);
            expect(quizSubmissionUpdateSpy).not.toHaveBeenCalledWith(5, syncedSubmission);
            expectSyncedSubmissions(submission, syncedSubmission);
        }));
    });

    it('should submit exam when end confirmed', () => {
        const studentExam = new StudentExam();
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(studentExam));
        comp.exam = new Exam();
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExam);
    });

    it('should show error', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const submitSpy = jest.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const alertErrorSpy = jest.spyOn(alertService, 'error');
        comp.exam = new Exam();
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
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
            expect(comp.isVisible()).toBeTrue();
            setComponentWithoutTestRun();
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
            expect(comp.isActive()).toBeTrue();
            setComponentWithoutTestRun();
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
        comp.exam = new Exam();
        comp.examEnded();
        expect(clearIntervalSpy).toHaveBeenCalledWith(comp.autoSaveInterval);
    });

    it('should trigger save and initialize exercise when exercise changed', () => {
        comp.exerciseIndex = 0;
        const exercise1 = new TextExercise(new Course(), undefined);
        exercise1.id = 15;
        const exercise2 = new ProgrammingExercise(new Course(), undefined);
        exercise2.id = 42;
        comp.studentExam = new StudentExam();
        comp.studentExam.exercises = [exercise1, exercise2];
        const triggerSpy = jest.spyOn(comp, 'triggerSave');
        const exerciseChange = { overViewChange: false, exercise: exercise2, forceSave: true };
        const createParticipationForExerciseSpy = jest.spyOn(comp, 'createParticipationForExercise').mockReturnValue(of(new StudentParticipation()));
        comp.exam = new Exam();
        comp.onPageChange(exerciseChange);
        expect(triggerSpy).toHaveBeenCalledWith(true, false);
        expect(comp.exerciseIndex).toBe(1);
        expect(createParticipationForExerciseSpy).toHaveBeenCalledWith(exercise2);
    });
});
