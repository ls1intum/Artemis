import { signal } from '@angular/core';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { UMLDiagramType } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamPage } from 'app/exam/shared/entities/exam-page.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamBarComponent } from 'app/exam/overview/exam-bar/exam-bar.component';
import { ExamParticipationCoverComponent } from 'app/exam/overview/exam-cover/exam-participation-cover.component';
import { ExamNavigationSidebarComponent } from 'app/exam/overview/exam-navigation-sidebar/exam-navigation-sidebar.component';
import { ExamLiveEvent, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import { ExamParticipationComponent } from 'app/exam/overview/exam-participation/exam-participation.component';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamExerciseOverviewPageComponent } from 'app/exam/overview/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { FileUploadExamSubmissionComponent } from 'app/exam/overview/exercises/file-upload/file-upload-exam-submission.component';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/overview/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/overview/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/overview/exercises/text/text-exam-submission.component';
import { ExamResultSummaryComponent } from 'app/exam/overview/summary/exam-result-summary.component';
import { FileUploadSubmissionService } from 'app/fileupload/overview/file-upload-submission.service';
import { ModelingSubmissionService } from 'app/modeling/overview/modeling-submission/modeling-submission.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming/shared/services/programming-submission.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { JhiConnectionStatusComponent } from 'app/shared-ui/connection-status/connection-status.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { DialogService } from 'primeng/dynamicdialog';
import { Subject, of, throwError } from 'rxjs';
import { skip } from 'rxjs/operators';
import { MockExamParticipationLiveEventsService } from 'test/helpers/mocks/service/mock-exam-participation-live-events.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
describe('ExamParticipationComponent', () => {
    // Backing signal for the mocked submission-sync contract; see the MockProvider below.
    const submissionSyncVersion = signal(0);
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
    let examExerciseUpdateService: ExamExerciseUpdateService;
    let translateService: TranslateService;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let examManagementService: ExamManagementService;

    function setupActivatedRouteMock() {
        return {
            parent: {
                parent: {
                    parent: {
                        params: of({}),
                    },
                    params: of({ courseId: '1' }),
                },
                params: of({}),
            },
            params: of({ examId: '2', testRunId: '3' }),
        };
    }

    /**
     * Points the route at a test exam by giving it the child route that carries the `studentExamId`, or clears it again
     * to model a regular exam. The component reads this child snapshot directly, so tests that navigate between the two
     * kinds of exam have to switch it explicitly.
     */
    function setRouteStudentExamId(activatedRoute: ActivatedRoute, studentExamId: string | undefined): void {
        (activatedRoute as any).firstChild = studentExamId ? { snapshot: { params: { studentExamId } } } : undefined;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                MockComponent(ExamExerciseOverviewPageComponent),
                ExamParticipationComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamParticipationCoverComponent),
                MockComponent(ExamBarComponent),
                MockComponent(ExamNavigationSidebarComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockComponent(JhiConnectionStatusComponent),
                MockDirective(TranslateDirective),
                MockComponent(TestRunRibbonComponent),
                MockComponent(ExamResultSummaryComponent),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                {
                    provide: AccountService,
                    useClass: MockAccountService,
                },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useClass: MockWebsocketService },
                {
                    provide: ActivatedRoute,
                    useValue: setupActivatedRouteMock(),
                },
                { provide: ExamParticipationLiveEventsService, useClass: MockExamParticipationLiveEventsService },
                // submissionSyncVersion is a field-initialised readonly signal, which ng-mocks' MockProvider
                // does not stub (it only mocks prototype members). The exam exercise overview page and the
                // save button read it to stay reactive to in-place `isSynced` mutations, so without it they
                // throw "submissionSyncVersion is not a function" as soon as they render. Wire the notifier to
                // the same signal instance too: a no-op notifier would leave those bindings permanently stale,
                // which is the staleness bug this contract exists to prevent.
                MockProvider(ExamParticipationService, {
                    submissionSyncVersion: submissionSyncVersion.asReadonly(),
                    notifySubmissionSyncStateChanged: () => submissionSyncVersion.update((version) => version + 1),
                    setSubmissionSaving: vi.fn(),
                }),
                MockProvider(ModelingSubmissionService),
                MockProvider(ProgrammingSubmissionService),
                MockProvider(TextSubmissionService),
                MockProvider(FileUploadSubmissionService),
                MockProvider(ArtemisServerDateService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
                MockProvider(ArtemisDatePipe),
                MockProvider(ExamManagementService),
                MockProvider(DialogService),
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();
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
        examExerciseUpdateService = TestBed.inject(ExamExerciseUpdateService);
        translateService = TestBed.inject(TranslateService);
        courseService = TestBed.inject(CourseManagementService);
        courseStorageService = TestBed.inject(CourseStorageService);
        examManagementService = TestBed.inject(ExamManagementService);
        // Ensure the mocked service has the currentlyLoadedStudentExam Subject in place; otherwise pipelines triggered
        // by tests below would crash with "Cannot read 'next' of undefined" during teardown.
        examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        // The TestBed has no router routes registered, so any navigate(...) call would emit an
        // unhandled NG04002 rejection. Stub it once so individual tests don't have to.
        vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        // Stub ngOnInit-triggered service calls with non-emitting Observables so ngOnInit only sets the route-derived
        // identifiers (courseId/examId/testRunId) without polluting comp.studentExam().
        const loadTestRunSpy = vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(new Subject());
        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(new Subject());
        vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(new Subject());
        comp.ngOnInit();
        loadTestRunSpy.mockClear();
        comp.exam.set(new Exam());
    });

    afterEach(() => {
        vi.restoreAllMocks();
        comp.ngOnDestroy();
    });

    it('should initialize', () => {
        fixture.changeDetectorRef.detectChanges();
        expect(ExamParticipationComponent).toBeTruthy();
    });

    describe('ExamParticipationSummaryComponent for TestRuns', () => {
        it('should initialize and display test run ribbon', () => {
            fixture.changeDetectorRef.detectChanges();
            expect(fixture).toBeTruthy();
            expect(!!comp.testRunId()).toBe(true);
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).toBeDefined();
        });
        it('should initialize and not display test run ribbon', () => {
            TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
            comp.exam().id = 2;
            comp.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            expect(fixture).toBeTruthy();
            expect(!!comp.testRunId()).toBe(false);
            const testRunRibbon = fixture.debugElement.query(By.css('#testRunRibbon'));
            expect(testRunRibbon).toBeNull();
        });
    });

    describe('isProgrammingExercise', () => {
        it('should return true if active exercise is a programming exercise', () => {
            comp.activeExamPage().exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExercise()).toBe(true);
        });
        it('should return false if active exercise is not a programming exercise', () => {
            comp.activeExamPage().exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            expect(comp.isProgrammingExercise()).toBe(false);
        });
    });

    describe('isProgrammingExerciseWithCodeEditor', () => {
        it('should return true if programming exercise is with code editor', () => {
            comp.activeExamPage().exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithCodeEditor()).toBe(false);
            (comp.activeExamPage().exercise as ProgrammingExercise).allowOnlineEditor = true;
            expect(comp.isProgrammingExerciseWithCodeEditor()).toBe(true);
        });
    });

    describe('isProgrammingExerciseWithOfflineIDE', () => {
        it('should return true if active exercise is with offline ide', () => {
            comp.activeExamPage().exercise = new ProgrammingExercise(new Course(), undefined);
            expect(comp.isProgrammingExerciseWithOfflineIDE()).toBe(true);
            (comp.activeExamPage().exercise as ProgrammingExercise).allowOfflineIde = false;
            expect(comp.isProgrammingExerciseWithOfflineIDE()).toBe(false);
        });
    });

    it('should load test run if test run id is defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        const loadTestRunStub = vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        comp.ngOnInit();
        expect(loadTestRunStub).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExam);
        expect(comp.exam()).toEqual(studentExam.exam);
    });

    it('should load exam if test run id is not defined', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        const studentExamWithExercises = { id: 1, numberOfExamSessions: 0, exam: new Exam() };
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExam);
        expect(comp.exam()).toEqual(studentExam.exam);
        expect(loadStudentExamWithExercisesForSummary).not.toHaveBeenCalled();
        studentExam.exam.course = new Course();
        studentExam.ended = true;
        studentExam.submitted = true;
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledTimes(2);
        expect(loadStudentExamWithExercisesForSummary).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExamWithExercises);
        expect(comp.studentExam()).not.toEqual(studentExam);
    });

    it('should redirect to exam summary after test run is over', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', testRunId: '3' });
        const router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
        const loadTestRunWithExercisesForConductionSpy = vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        const submitStudentExamSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        comp.ngOnInit();
        expect(loadTestRunWithExercisesForConductionSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExam);
        comp.onExamEndConfirmed();
        expect(submitStudentExamSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['course-management', 1, 'exams', 2, 'test-runs', 3, 'summary']);
    });

    it('should load new testExam if studentExam id is start', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: 'start' });
        const loadTestRunStub = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        comp.ngOnInit();
        expect(loadTestRunStub).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExam);
        expect(comp.exam()).toEqual(studentExam.exam);
    });

    it('should load existing testExam if studentExam id is start', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 150;
        studentExam.id = 4;
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.id = 4;
        studentExamWithExercises.exam = new Exam();
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: 'start' });
        const loadStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExam);
        expect(comp.exam()).toEqual(studentExam.exam);
        expect(comp.studentExam().id).toEqual(studentExam.id);
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
        studentExamWithExercises.exam = new Exam();
        const activatedRoute = TestBed.inject(ActivatedRoute);
        setRouteStudentExamId(activatedRoute, '3');
        activatedRoute.params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const loadStudentExamWithExercisesForSummary = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        studentExam.exam.course = new Course();
        studentExam.ended = true;
        studentExam.submitted = true;
        comp.ngOnInit();
        expect(loadStudentExamSpy).not.toHaveBeenCalled();
        expect(loadStudentExamWithExercisesForSummary).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(studentExamWithExercises);
        expect(comp.studentExam()).not.toEqual(studentExam);
        expect(comp.studentExam().id).toEqual(studentExamWithExercises.id);
    });

    it('should load exam from local storage if needed', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.id = 1;
        const loadStudentExamStub = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));

        const localStudentExam = new StudentExam();
        localStudentExam.exam = studentExam.exam;
        localStudentExam.id = 2; // use a different id for testing purposes only
        localStudentExam.exercises = [];
        const lastSaveFailedStub = vi.spyOn(examParticipationService, 'lastSaveFailed').mockReturnValue(true);
        const loadLocalStudentExamStub = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage').mockReturnValue(of(localStudentExam));

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });

        comp.ngOnInit();

        expect(loadStudentExamStub).toHaveBeenCalledOnce();
        expect(lastSaveFailedStub).toHaveBeenCalledOnce();
        expect(loadLocalStudentExamStub).toHaveBeenCalledOnce();
        expect(comp.studentExam()).toEqual(localStudentExam);
        expect(comp.studentExam()).not.toEqual(studentExam);
        expect(comp.exam()).toEqual(studentExam.exam);
    });

    it('should determine tutor status if no exam was loaded', () => {
        const httpError = new HttpErrorResponse({
            error: { errorKey: 'No student exam for you' },
            status: 400,
        });
        const course: Course = { isAtLeastTutor: true };

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => httpError));
        const courseStorageServiceSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(courseStorageServiceSpy).toHaveBeenCalledOnce();
        expect(comp.isAtLeastTutor()).toBe(true);
    });

    it('should determine tutor status if no exam was loaded and course was not cached', () => {
        const httpError = new HttpErrorResponse({
            error: { errorKey: 'No student exam for you' },
            status: 400,
        });
        const course: Course = { isAtLeastTutor: true };

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => httpError));
        const courseStorageServiceSpy = vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
        const courseServiceSpy = vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(courseStorageServiceSpy).toHaveBeenCalledOnce();
        expect(courseServiceSpy).toHaveBeenCalledOnce();
        expect(comp.isAtLeastTutor()).toBe(true);
    });

    /**
     * @param studentExam    the student exam to start
     * @param prepareForStart installs the exam state under test. It runs after ngOnInit, because the route emission
     *                        there resets whatever the previously displayed exam left behind, exactly as a real
     *                        navigation does, so state arranged before it would be dropped again.
     */
    const testExamStarted = (studentExam: StudentExam, prepareForStart?: () => void) => {
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
        const latestPendingSubmissionSpy = (programmingSubmissionService.getLatestPendingSubmissionByParticipationId = vi.fn().mockReturnValue(
            of({
                submission: new ProgrammingSubmission(),
                participationId: 2,
                submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            } as ProgrammingSubmissionStateObj),
        ));
        comp.ngOnInit();
        prepareForStart?.();
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
        expect(firstExercise.studentParticipations![0].submissions![0].submitted).toBe(true);

        // Sync exercises with submission
        const secondSubmission = secondExercise.studentParticipations![0].submissions![0];
        expect(secondSubmission.isSynced).toBe(true);
        expect(secondSubmission.submitted).toBe(false);

        if (studentExam.testRun || studentExam.exam?.testExam) {
            expect(comp.individualStudentEndDate()).toEqual(comp.testStartTime()!.add(studentExam.workingTime!, 'seconds'));
        } else {
            expect(comp.individualStudentEndDate()).toEqual(comp.exam().startDate!.add(studentExam.workingTime!, 'seconds'));
        }

        // Initialize Exam Overview Page
        expect(comp.activeExamPage().exercise).toBeUndefined();
        expect(comp.activeExamPage().isOverviewPage).toBe(true);
    };

    it('should initialize exercises when exam starts', () => {
        const studentExam = new StudentExam();
        studentExam.workingTime = 100;
        studentExam.testRun = true;
        testExamStarted(studentExam, () => {
            comp.testStartTime.set(dayjs().subtract(1000, 'seconds'));
            comp.exam.set(new Exam());
        });
    });

    it('should initialize test exam', () => {
        const studentExam = new StudentExam();
        const exam = new Exam();
        exam.testExam = true;
        studentExam.exam = exam;
        studentExam.workingTime = 100;
        testExamStarted(studentExam, () => {
            comp.testStartTime.set(dayjs().subtract(1000, 'seconds'));
            comp.exam.set(exam);
        });
    });

    it('should initialize exercise without test run', () => {
        // Should calculate time from exam start date when no test run, rest does not get effected
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const startDate = dayjs();
        const workingTime = 1000;
        const studentExam = new StudentExam();
        studentExam.workingTime = workingTime;
        testExamStarted(studentExam, () => {
            comp.exam.set(new Exam());
            comp.exam().startDate = dayjs(startDate);
        });
        expect(comp.individualStudentEndDate()).toEqual(startDate.add(workingTime, 'seconds'));
    });

    it('should create participation for given exercise', () => {
        comp.exam.set(new Exam());
        comp.exam().course = new Course();
        const createdParticipation = new StudentParticipation();
        const programmingSubmission = new ProgrammingSubmission();
        createdParticipation.submissions = [programmingSubmission];
        createdParticipation.exercise = new ProgrammingExercise(new Course(), undefined);
        const courseExerciseServiceStub = vi.spyOn(courseExerciseService, 'startExercise').mockReturnValue(of(createdParticipation));
        vi.spyOn(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(of(undefined as any));
        const exercise = new ProgrammingExercise(new Course(), undefined);
        const seenStates: string[] = [];
        comp.generateParticipationStatus.pipe(skip(1)).subscribe((state) => {
            seenStates.push(state);
        });

        let receivedParticipation: StudentParticipation | undefined;
        comp.createParticipationForExercise(exercise).subscribe((participation) => {
            receivedParticipation = participation;
        });

        expect(seenStates).toEqual(['generating', 'success']);
        expect(programmingSubmission.isSynced).toBe(true);
        expect(receivedParticipation).toEqual(createdParticipation);
        expect(courseExerciseServiceStub).toHaveBeenCalledOnce();
    });

    it('should generate participation state when participation creation fails', () => {
        comp.exam.set(new Exam());
        comp.exam().course = new Course();
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const courseExerciseServiceStub = vi.spyOn(courseExerciseService, 'startExercise').mockReturnValue(throwError(() => httpError));
        const seenStates: string[] = [];
        comp.generateParticipationStatus.pipe(skip(1)).subscribe((state) => {
            seenStates.push(state);
        });
        const exercise = new ProgrammingExercise(new Course(), undefined);
        comp.createParticipationForExercise(exercise).subscribe();
        expect(seenStates).toEqual(['generating', 'failed']);
        expect(courseExerciseServiceStub).toHaveBeenCalledOnce();
    });

    describe('websocket working time subscription', () => {
        const startDate = dayjs('2022-02-21T23:00:00+01:00');

        beforeEach(() => {
            comp.studentExam.set({ id: 3, workingTime: 420, numberOfExamSessions: 0 });
            comp.studentExamId.set(comp.studentExam().id!);
            examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        });

        it('should correctly increase working time', () => {
            const event = {
                newWorkingTime: 1337,
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = vi.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam().workingTime).toBe(1337);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });

        it('should update the exam schedule and end date when the start date changes (issue #13071)', () => {
            const newStartDate = startDate.add(30, 'minutes');
            const newEndDate = newStartDate.add(comp.studentExam().workingTime!, 'seconds');
            const event = {
                newWorkingTime: comp.studentExam().workingTime,
                newStartDate,
                newEndDate,
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));

            comp.initIndividualEndDates(startDate);

            // The exam start/end must reflect the pushed schedule so the pre-start countdown and start-based visibility recompute.
            expect(comp.exam().startDate!.isSame(newStartDate)).toBe(true);
            expect(comp.exam().endDate!.isSame(newEndDate)).toBe(true);
            // The individual end date must be derived from the NEW start, not the stale one captured on subscription.
            expect(comp.individualStudentEndDate()).toEqual(newStartDate.add(comp.studentExam().workingTime!, 'seconds'));
        });

        it('should keep the exam dates untouched when the event carries no schedule, e.g. a test exam (issue #13071)', () => {
            // A test-exam working time update omits the schedule (the exam dates are only its availability window). The
            // exam's own dates must be preserved and the end date derived from the student's start captured on subscription.
            const originalStartDate = dayjs('2022-02-21T22:00:00+01:00');
            const originalEndDate = originalStartDate.add(2, 'hours');
            comp.exam.set({ ...comp.exam(), startDate: originalStartDate, endDate: originalEndDate });
            const event = {
                newWorkingTime: comp.studentExam().workingTime,
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));

            comp.initIndividualEndDates(startDate);

            // The exam's own start/end must not be overwritten with the (per-student) subscription start.
            expect(comp.exam().startDate!.isSame(originalStartDate)).toBe(true);
            expect(comp.exam().endDate!.isSame(originalEndDate)).toBe(true);
            // The individual end date is derived from the start captured on subscription (the student's start for test exams).
            expect(comp.individualStudentEndDate()).toEqual(startDate.add(comp.studentExam().workingTime!, 'seconds'));
        });

        it('should correctly increase working time to next day', () => {
            const event = {
                newWorkingTime: 9001,
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = vi.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            // the following line uses the current time zone and therefore avoids a time zone flaky test
            // (if left out, the test would pass in the German time zone and fail in most other time zones)
            const startDate = dayjs().set('h', 23); //today at 23:00
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam().workingTime).toBe(9001);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });

        it('should correctly decrease working time', () => {
            const event = {
                newWorkingTime: 42,
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            const ackSpy = vi.spyOn(examParticipationLiveEventsService, 'acknowledgeEvent');
            comp.initIndividualEndDates(startDate);
            expect(comp.studentExam().workingTime).toBe(42);
            expect(ackSpy).toHaveBeenCalledExactlyOnceWith(event, false);
        });
    });

    describe('websocket problem statement update subscription', () => {
        beforeEach(() => {
            comp.studentExam.set(new StudentExam());
            comp.exam.set(new Exam());
            const textExercise = new TextExercise(new Course(), undefined);
            textExercise.id = 1;
            textExercise.problemStatement = 'old problem statement text exercise';
            const programmingExercise = new ProgrammingExercise(new Course(), undefined);
            programmingExercise.id = 2;
            programmingExercise.problemStatement = 'old problem statement programming exercise';
            comp.studentExam().exercises = [textExercise, programmingExercise];
        });

        it('should correctly update problem statement if exercise was not opened yet', () => {
            const event = {
                problemStatement: 'new problem statement',
                exerciseId: 2,
                exerciseName: 'exercise1',
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            vi.spyOn(examExerciseUpdateService, 'updateLiveExamExercise');
            comp.examStarted(comp.studentExam());
            comp['subscribeToProblemStatementUpdates']();
            expect(examExerciseUpdateService.updateLiveExamExercise).not.toHaveBeenCalled();
            expect(comp.studentExam().exercises![1].problemStatement).toBe('new problem statement');
        });

        it('should correctly update problem statement if exercise was previously opened', () => {
            const event = {
                problemStatement: 'new problem statement',
                exerciseId: 2,
                exerciseName: 'exercise1',
            } as any as ExamLiveEvent;
            vi.spyOn(examParticipationLiveEventsService, 'observeNewEventsAsSystem').mockReturnValue(of(event));
            vi.spyOn(examExerciseUpdateService, 'updateLiveExamExercise');
            comp.examStarted(comp.studentExam());
            comp.pageComponentVisited()[1] = true;
            comp['subscribeToProblemStatementUpdates']();
            expect(examExerciseUpdateService.updateLiveExamExercise).toHaveBeenCalledExactlyOnceWith(2, 'new problem statement');
        });
    });

    describe('trigger save', () => {
        let textSubmissionUpdateSpy: ReturnType<typeof vi.spyOn>;
        let modelingSubmissionUpdateSpy: ReturnType<typeof vi.spyOn>;
        let quizSubmissionUpdateSpy: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            comp.studentExam.set(new StudentExam());
            comp.exam.set(new Exam());
        });

        const expectSyncedSubmissions = (submission: Submission, syncedSubmission: Submission) => {
            expect(submission.isSynced).toBe(true);
            expect(submission.submitted).toBe(true);
            expect(syncedSubmission.isSynced).toBe(true);
            expect(syncedSubmission.submitted).toBe(false);
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
            comp.studentExam().exercises = [textExercise];
            textSubmissionUpdateSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
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
            comp.studentExam().exercises = [modelingExercise];
            modelingSubmissionUpdateSpy = vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
            comp.triggerSave(false);
            expect(modelingSubmissionUpdateSpy).toHaveBeenCalledWith(submission, 5);
            expect(modelingSubmissionUpdateSpy).not.toHaveBeenCalledWith(syncedSubmission, 5);
            expectSyncedSubmissions(submission, syncedSubmission);
        });

        it('should sync quiz submissions', async () => {
            const quizExercise = new QuizExercise(new Course(), undefined);
            quizExercise.id = 5;
            const participation = new StudentParticipation();
            const submission = new QuizSubmission();
            const syncedSubmission = new QuizSubmission();
            syncedSubmission.isSynced = true;
            participation.submissions = [submission, syncedSubmission];
            quizExercise.studentParticipations = [participation];
            comp.studentExam().exercises = [quizExercise];
            quizSubmissionUpdateSpy = vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(submission));
            comp.triggerSave(false);
            await new Promise((resolve) => setTimeout(resolve, 500));
            expect(quizSubmissionUpdateSpy).toHaveBeenCalledWith(5, submission);
            expect(quizSubmissionUpdateSpy).not.toHaveBeenCalledWith(5, syncedSubmission);
            expectSyncedSubmissions(submission, syncedSubmission);
        });
    });

    describe('resume from local storage after a failed save', () => {
        const buildResumeStudentExam = () => {
            const exam = new Exam();
            exam.startDate = dayjs().subtract(10, 'minutes');
            exam.gracePeriod = 180;

            const quizExercise = new QuizExercise(new Course(), undefined);
            quizExercise.id = 11;
            const quizParticipation = new StudentParticipation();
            const quizSubmission = new QuizSubmission();
            quizSubmission.isSynced = false; // entered but not yet saved to the server (e.g. a save failed during an outage)
            quizParticipation.submissions = [quizSubmission];
            quizExercise.studentParticipations = [quizParticipation];

            const textExercise = new TextExercise(new Course(), undefined);
            textExercise.id = 12;
            const textParticipation = new StudentParticipation();
            const textSubmission = new TextSubmission();
            textSubmission.isSynced = false;
            textParticipation.submissions = [textSubmission];
            textExercise.studentParticipations = [textParticipation];

            const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, new Course(), undefined);
            modelingExercise.id = 13;
            const modelingParticipation = new StudentParticipation();
            const modelingSubmission = new ModelingSubmission();
            modelingSubmission.isSynced = false;
            modelingParticipation.submissions = [modelingSubmission];
            modelingExercise.studentParticipations = [modelingParticipation];

            const studentExam = new StudentExam();
            studentExam.exam = exam;
            studentExam.workingTime = 3600;
            studentExam.exercises = [quizExercise, textExercise, modelingExercise];
            return { studentExam, quizSubmission, textSubmission, modelingSubmission };
        };

        it('should re-send restored but not-yet-saved quiz, text and modeling answers when resuming', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            comp.exam.set(studentExam.exam!);
            comp.connected.set(true);
            // The mocked submission services return synchronous observables, so the re-send happens synchronously.
            const quizSpy = vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            const textSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: textSubmission })));
            const modelingSpy = vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));

            comp.examStarted(studentExam, true);

            // All three not-yet-saved answers must be re-sent to the server instead of being silently dropped.
            expect(textSpy).toHaveBeenCalledWith(textSubmission, 12);
            expect(modelingSpy).toHaveBeenCalledWith(modelingSubmission, 13);
            expect(quizSpy).toHaveBeenCalledWith(11, quizSubmission);
        });

        it('should force the recovery re-send even when the websocket is not (re)connected yet at resume', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            comp.exam.set(studentExam.exam!);
            // Right after a reload the websocket often has not re-established yet (especially in a multi-node cluster),
            // so `connected()` is false. The recovery re-send is a plain HTTP request that does not need the websocket,
            // and it must fire regardless — otherwise the restored answers are silently deferred to the next autosave
            // cycle, the answer-loss window this recovery path exists to close (regression: ExamSubmissionRecovery E2E).
            comp.connected.set(false);
            const quizSpy = vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            const textSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: textSubmission })));
            const modelingSpy = vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));

            comp.examStarted(studentExam, true);

            // All three restored answers are re-sent immediately, not deferred, despite the websocket being down.
            expect(quizSpy).toHaveBeenCalledWith(11, quizSubmission);
            expect(textSpy).toHaveBeenCalledWith(textSubmission, 12);
            expect(modelingSpy).toHaveBeenCalledWith(modelingSubmission, 13);
        });

        it('should not show the restore notification on a normal (not failed) start', () => {
            const studentExam = new StudentExam();
            studentExam.exam = new Exam();
            studentExam.exam.startDate = dayjs().subtract(10, 'minutes');
            studentExam.id = 1;
            studentExam.workingTime = 3600;
            studentExam.exercises = [];
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
            vi.spyOn(examParticipationService, 'lastSaveFailed').mockReturnValue(false); // no failed save -> no restore
            const loadLocalSpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage');
            const infoSpy = vi.spyOn(alertService, 'info');

            TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
            comp.ngOnInit();

            expect(loadLocalSpy).not.toHaveBeenCalled();
            expect(infoSpy).not.toHaveBeenCalledWith('artemisApp.examParticipation.answersRestoredFromLocalStorage');
        });

        it('should not re-send anything and mark submissions synced on a normal (fresh) start', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            comp.exam.set(studentExam.exam!);
            const quizSpy = vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            const textSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: textSubmission })));
            const modelingSpy = vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));

            comp.examStarted(studentExam); // fresh start: resumedFromFailedSave defaults to false

            expect(textSpy).not.toHaveBeenCalled();
            expect(modelingSpy).not.toHaveBeenCalled();
            expect(quizSpy).not.toHaveBeenCalled();
            expect(quizSubmission.isSynced).toBe(true);
            expect(textSubmission.isSynced).toBe(true);
            expect(modelingSubmission.isSynced).toBe(true);
        });

        it('should inform the student that restored answers are being saved when resuming from local storage', () => {
            const studentExam = new StudentExam();
            studentExam.exam = new Exam();
            studentExam.id = 1;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
            const localStudentExam = new StudentExam();
            localStudentExam.exam = studentExam.exam;
            localStudentExam.exam.startDate = dayjs().subtract(10, 'minutes');
            localStudentExam.id = 2;
            localStudentExam.workingTime = 3600;
            localStudentExam.exercises = [];
            vi.spyOn(examParticipationService, 'lastSaveFailed').mockReturnValue(true);
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage').mockReturnValue(of(localStudentExam));
            const infoSpy = vi.spyOn(alertService, 'info');

            TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
            comp.ngOnInit();

            expect(infoSpy).toHaveBeenCalledWith('artemisApp.examParticipation.answersRestoredFromLocalStorage');
        });

        it('should keep a failed re-send unsynced and flag the failure so the autosave retries it later', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            comp.exam.set(studentExam.exam!);
            comp.connected.set(true);
            // the text re-send fails (still-flaky connection on resume), while quiz and modeling succeed
            vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));
            const textSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
            const setLastSaveFailedSpy = vi.spyOn(examParticipationService, 'setLastSaveFailed');

            comp.examStarted(studentExam, true);

            expect(textSpy).toHaveBeenCalledWith(textSubmission, 12);
            // the failed answer must stay unsynced so the autosave timer re-sends it later instead of it being silently lost
            expect(textSubmission.isSynced).toBe(false);
            expect(setLastSaveFailedSpy).toHaveBeenCalledWith(true, expect.anything(), expect.anything());
            // the flag must NOT be reset to false by the later successful quiz/modeling saves while the text answer is
            // still unsynced - otherwise a reload would skip restoring and re-sending it.
            expect(setLastSaveFailedSpy).not.toHaveBeenCalledWith(false, expect.anything(), expect.anything());
            expect(setLastSaveFailedSpy.mock.calls.at(-1)?.[0]).toBe(true);
            // the answers that did save are now synced
            expect(quizSubmission.isSynced).toBe(true);
            expect(modelingSubmission.isSynced).toBe(true);
        });

        it('should clear the failed-save flag once every restored answer is successfully re-sent', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            comp.exam.set(studentExam.exam!);
            comp.connected.set(true);
            vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: textSubmission })));
            vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));
            const setLastSaveFailedSpy = vi.spyOn(examParticipationService, 'setLastSaveFailed');

            comp.examStarted(studentExam, true);

            // once all restored answers are synced, the flag is cleared so a later reload no longer enters the restore path
            expect(quizSubmission.isSynced).toBe(true);
            expect(textSubmission.isSynced).toBe(true);
            expect(modelingSubmission.isSynced).toBe(true);
            expect(setLastSaveFailedSpy.mock.calls.at(-1)?.[0]).toBe(false);
        });

        it('should re-send only not-yet-saved answers and leave already-synced ones untouched when resuming', () => {
            const { studentExam, quizSubmission, textSubmission, modelingSubmission } = buildResumeStudentExam();
            // the text answer had already been saved before the failure; only quiz and modeling are still pending
            textSubmission.isSynced = true;
            comp.exam.set(studentExam.exam!);
            comp.connected.set(true);
            const quizSpy = vi.spyOn(examParticipationService, 'updateQuizSubmission').mockReturnValue(of(quizSubmission));
            const textSpy = vi.spyOn(textSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: textSubmission })));
            const modelingSpy = vi.spyOn(modelingSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: modelingSubmission })));

            comp.examStarted(studentExam, true);

            // an already-synced answer must not be re-sent (avoids overwriting good server state / a duplicate submission)
            expect(textSpy).not.toHaveBeenCalled();
            expect(quizSpy).toHaveBeenCalledWith(11, quizSubmission);
            expect(modelingSpy).toHaveBeenCalledWith(modelingSubmission, 13);
        });
    });

    it('should submit exam when end confirmed', () => {
        comp.studentExam.set(new StudentExam());
        comp.studentExam().submitted = false;
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        comp.exam.set(new Exam());
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(comp.studentExam()?.submitted).toBe(true);
    });

    it('should leave the hand-in-early view after a successful early submission so the confirmation panel can show', () => {
        // Regression for the delayed-summary flow: after handing in early the student stayed on the hand-in-early cover with a
        // disabled Finish button, because handInEarly was never reset. With a publication date in the future there is also no
        // summary to navigate to, so nothing else moved the student off that screen.
        comp.studentExam.set(new StudentExam());
        comp.studentExam().submitted = false;
        const exam = new Exam();
        exam.examSummaryPublicationDate = dayjs().add(1, 'day');
        comp.exam.set(exam);
        comp.handInEarly.set(true);
        vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));

        comp.onExamEndConfirmed();

        expect(comp.studentExam()?.submitted).toBe(true);
        expect(comp.handInEarly()).toBe(false);
    });

    it('should clear the failed-save flag once the exam is successfully submitted', () => {
        comp.studentExam.set(new StudentExam());
        comp.studentExam().submitted = false;
        comp.exam.set(new Exam());
        vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        const setLastSaveFailedSpy = vi.spyOn(examParticipationService, 'setLastSaveFailed');

        comp.onExamEndConfirmed();

        // A lingering failed-save flag would otherwise re-enter the restore path on a reload before the exam ends and
        // re-send answers for an already-submitted exam.
        expect(setLastSaveFailedSpy).toHaveBeenCalledWith(false, expect.anything(), expect.anything());
    });

    it('should show error when already submitted for test run and successfully loading student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        const loadTestRunWithExercisesForConductionSpy = vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        comp.exam.set(new Exam());
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(loadTestRunWithExercisesForConductionSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).not.toHaveBeenCalled();
        expect(comp.studentExam()).toEqual(studentExam);
    });

    it('should show error when already submitted for test run and failed to load student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const loadTestRunWithExercisesForConductionSpy = vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(throwError(() => new Error()));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        comp.exam.set(new Exam());
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(loadTestRunWithExercisesForConductionSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when already submitted and successfully loading student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const studentExam = new StudentExam();
        const getOwnStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        comp.exam.set(new Exam());
        comp.testRunId.set(0);
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(getOwnStudentExamSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).not.toHaveBeenCalled();
        expect(comp.studentExam()).toEqual(studentExam);
    });

    it('should show error when already submitted and failed to load student exam', () => {
        const httpError = new Error();
        httpError.message = 'artemisApp.studentExam.alreadySubmitted';
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const getOwnStudentExamSpy = vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => new Error()));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        comp.exam.set(new Exam());
        comp.testRunId.set(0);
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(getOwnStudentExamSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when not submitted', () => {
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const submitSpy = vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(throwError(() => httpError));
        const alertErrorSpy = vi.spyOn(alertService, 'error');
        comp.exam.set(new Exam());
        comp.onExamEndConfirmed();
        expect(submitSpy).toHaveBeenCalledOnce();
        expect(alertErrorSpy).toHaveBeenCalledOnce();
    });

    describe('canDeactivate', () => {
        it('should return true if logout is true', () => {
            comp.loggedOut.set(true);
            expect(comp.canDeactivate()).toBe(true);
        });

        it('should call translateService', () => {
            const translateServiceSpy = vi.spyOn(translateService, 'instant');
            const canDeactivate = comp.canDeactivateWarning;
            expect(canDeactivate).toBe('artemisApp.examParticipation.pendingChanges');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
        });
    });

    describe('isOver', () => {
        it('should return true if exam has ended', () => {
            const studentExam = new StudentExam();
            studentExam.ended = true;
            comp.studentExam.set(studentExam);
            expect(comp.isOver()).toBe(true);
        });
        it('should return true when handed in early', () => {
            comp.handInEarly.set(true);
            expect(comp.isOver()).toBe(true);
        });
        it('should return true if student exam has been submitted', () => {
            const studentExam = new StudentExam();
            studentExam.submitted = true;
            comp.studentExam.set(studentExam);
            expect(comp.isOver()).toBe(true);
        });
        it('should be over if individual end date is before server date', () => {
            const endDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.individualStudentEndDate.set(endDate);
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isOver()).toBe(true);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
        it('should not be over if individual end date is after server date', () => {
            const endDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.individualStudentEndDate.set(endDate);
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isOver()).toBe(false);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    const setComponentWithoutTestRun = () => {
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
        comp.exam.set(new Exam());
    };

    describe('isExamSummaryVisible', () => {
        it('should be visible for a test run regardless of the summary publication date', () => {
            comp.exam.set(new Exam());
            comp.exam().examSummaryPublicationDate = dayjs().add(1, 'days');
            // the default component is set up as a test run
            expect(comp.isExamSummaryVisible()).toBe(true);
        });

        it('should be visible if no summary publication date is set', () => {
            setComponentWithoutTestRun();
            expect(comp.isExamSummaryVisible()).toBe(true);
        });

        it('should be hidden if the summary publication date is in the future', () => {
            setComponentWithoutTestRun();
            comp.exam().examSummaryPublicationDate = dayjs().add(1, 'days');
            vi.spyOn(artemisServerDateService, 'now').mockReturnValue(dayjs());
            expect(comp.isExamSummaryVisible()).toBe(false);
        });

        it('should be visible if the summary publication date is in the past', () => {
            setComponentWithoutTestRun();
            comp.exam().examSummaryPublicationDate = dayjs().subtract(1, 'minutes');
            vi.spyOn(artemisServerDateService, 'now').mockReturnValue(dayjs());
            expect(comp.isExamSummaryVisible()).toBe(true);
        });
    });

    describe('isVisible', () => {
        it('should be visible if test run', () => {
            expect(comp.isVisible()).toBe(true);
            setComponentWithoutTestRun();
            expect(comp.isVisible()).toBe(false);
        });

        it('should be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.exam().visibleDate = visibleDate;
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isVisible()).toBe(true);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be visible if visible date is before server date', () => {
            setComponentWithoutTestRun();
            const visibleDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.exam().visibleDate = visibleDate;
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isVisible()).toBe(false);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    describe('isActive', () => {
        it('should be active if test run', () => {
            expect(comp.isActive()).toBe(true);
            setComponentWithoutTestRun();
            expect(comp.isActive()).toBe(false);
        });

        it('should be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = dayjs().subtract(1, 'days');
            const date = dayjs();
            comp.exam().startDate = startDate;
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isActive()).toBe(true);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be active if start date is before server date', () => {
            setComponentWithoutTestRun();
            const startDate = dayjs().add(1, 'days');
            const date = dayjs();
            comp.exam().startDate = startDate;
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(date);
            expect(comp.isActive()).toBe(false);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be active if there is no exam and it is not a test run', () => {
            comp.testRunId.set(0);
            comp.exam.set(undefined!);
            expect(comp.isActive()).toBe(false);
        });
    });

    describe('isVisible without an exam', () => {
        it('should not be visible if there is no exam and it is not a test run', () => {
            comp.testRunId.set(0);
            comp.exam.set(undefined!);
            expect(comp.isVisible()).toBe(false);
        });
    });

    describe('sidebar toggle', () => {
        it('should register the sidebar toggle callback and invoke it', () => {
            const toggle = vi.fn();
            comp.setSidebarToggle(true, toggle);
            expect(comp.isSidebarCollapsed()).toBe(true);

            comp.toggleSidebar();
            expect(toggle).toHaveBeenCalledOnce();
        });

        it('should not fail when toggling the sidebar before a callback is registered', () => {
            expect(() => comp.toggleSidebar()).not.toThrow();
        });
    });

    describe('isGracePeriodOver', () => {
        it('should be falsy when the grace period end date is not set', () => {
            comp.individualStudentEndDateWithGracePeriod.set(undefined!);
            expect(comp.isGracePeriodOver()).toBeFalsy();
        });

        it('should be over when the grace period end date is before the server date', () => {
            comp.individualStudentEndDateWithGracePeriod.set(dayjs().subtract(1, 'days'));
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(dayjs());
            expect(comp.isGracePeriodOver()).toBe(true);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });

        it('should not be over when the grace period end date is after the server date', () => {
            comp.individualStudentEndDateWithGracePeriod.set(dayjs().add(1, 'days'));
            const serverNowSpy = vi.spyOn(artemisServerDateService, 'now').mockReturnValue(dayjs());
            expect(comp.isGracePeriodOver()).toBe(false);
            expect(serverNowSpy).toHaveBeenCalledOnce();
        });
    });

    it('should clear autoSaveInterval when exam ended', () => {
        const clearIntervalSpy = vi.spyOn(window, 'clearInterval');
        // captured before the call: the handle is dropped along with the interval, so reading it afterwards would
        // compare the spy against undefined instead of the interval that had to be cleared
        const autoSaveInterval = 1;
        comp.autoSaveInterval = autoSaveInterval;
        comp.studentExam.set(new StudentExam());
        comp.exam.set(new Exam());
        comp.examEnded();
        expect(clearIntervalSpy).toHaveBeenCalledWith(autoSaveInterval);
        expect(comp.autoSaveInterval).toBeUndefined();
    });

    describe('onPageChange', () => {
        it('should trigger save and initialize exercise when exercise changed', () => {
            comp.exerciseIndex.set(0);
            const exercise1 = new TextExercise(new Course(), undefined);
            exercise1.id = 15;
            const exercise2 = new ProgrammingExercise(new Course(), undefined);
            exercise2.id = 42;
            comp.studentExam.set(new StudentExam());
            comp.studentExam().exercises = [exercise1, exercise2];
            comp.pageComponentVisited.set([false, false]);
            const triggerSpy = vi.spyOn(comp, 'triggerSave');
            const exerciseChange = { overViewChange: false, exercise: exercise2, forceSave: true };
            const createParticipationForExerciseSpy = vi.spyOn(comp, 'createParticipationForExercise').mockReturnValue(of(new StudentParticipation()));
            vi.spyOn(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId').mockReturnValue(of(undefined as any));
            comp.exam.set(new Exam());
            comp.onPageChange(exerciseChange);
            expect(triggerSpy).toHaveBeenCalledWith(true);
            expect(comp.exerciseIndex()).toBe(1);
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
            const triggerSpy = vi.spyOn(comp, 'triggerSave');
            const exerciseChange = { overViewChange: false, exercise: exercise, forceSave: true };
            comp.exam.set(new Exam());
            comp.activeExamPage.set(new ExamPage());
            comp.activeExamPage().exercise = exercise;
            comp.studentExam.set(new StudentExam());
            comp.studentExam().exercises = [exercise];
            comp.pageComponentVisited.set([true]);
            comp.examStartConfirmed.set(true);

            comp.onPageChange(exerciseChange);

            expect(triggerSpy).toHaveBeenCalledWith(true);
            expect(comp.exerciseIndex()).toBe(0);
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
            comp.handInEarly.set(true);
            comp.studentExam.set(new StudentExam());
            comp.studentExam().exercises = [exercise1, exercise2, exercise3];
            comp.activeExamPage.set({
                isOverviewPage: false,
                exercise: exercise2,
            });
            comp.exerciseIndex.set(1);
            comp.pageComponentVisited.set([true, true, true]);

            // Spy on the private method resetPageComponentVisited
            const resetPageComponentVisitedSpy = vi.spyOn<any, any>(comp, 'resetPageComponentVisited');

            // Call toggleHandInEarly to change the handInEarly state
            comp.handleHandInEarly();

            // Verify that resetPageComponentVisited has been called with the correct index
            expect(resetPageComponentVisitedSpy).toHaveBeenCalledExactlyOnceWith(1);

            // Verify that the pageComponentVisited array and exerciseIndex are updated correctly
            expect(comp.pageComponentVisited()).toEqual([false, true, false]);
            expect(comp.exerciseIndex()).toBe(1);
        });

        it('should trigger save', () => {
            const triggerSaveSpy = vi.spyOn(comp, 'triggerSave').mockImplementation(() => {});
            comp.handInEarly.set(false);
            comp.handleHandInEarly();

            expect(triggerSaveSpy).toHaveBeenCalledOnce();
        });
    });

    describe('toggleHandInEarly', () => {
        it('should not fetch attendance check status if exam is a test exam', () => {
            comp.exam.set(new Exam());
            comp.exam().testExam = true;

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = vi.spyOn<any, any>(examManagementService, 'isAttendanceChecked');

            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should not fetch attendance check status if exam is not an exam with attendance check', () => {
            comp.exam.set(new Exam());
            comp.exam().examWithAttendanceCheck = false;

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = vi.spyOn<any, any>(examManagementService, 'isAttendanceChecked');

            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should not fetch attendance check status if user clicks continue', () => {
            comp.handInEarly.set(true);

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = vi.spyOn<any, any>(examManagementService, 'isAttendanceChecked');

            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has not been called
            expect(attendanceCheckSpy).not.toHaveBeenCalled();
        });

        it('should fetch attendance check status if exam is an exam with attendance check', () => {
            comp.exam.set(new Exam());
            comp.exam().examWithAttendanceCheck = true;

            // Spy on the method isAttendanceChecked
            const attendanceCheckSpy = vi.spyOn<any, any>(examManagementService, 'isAttendanceChecked').mockReturnValue(of(new HttpResponse({ body: true })));

            // Call toggleHandInEarly to change the handInEarly state
            comp.toggleHandInEarly();

            // Verify that isAttendanceChecked has been called
            expect(attendanceCheckSpy).toHaveBeenCalledOnce();
            expect(comp.attendanceChecked()).toBe(true);
        });
    });

    describe('activePageIndex', () => {
        it('should return -1 if active page is overview page', () => {
            comp.activeExamPage.set(new ExamPage());
            comp.activeExamPage().isOverviewPage = true;
            expect(comp.activePageIndex).toBe(-1);
        });

        it('should return the index of the active page', () => {
            const exercise0 = new QuizExercise(undefined, undefined);
            exercise0.id = 5;
            const exercise1 = new ProgrammingExercise(undefined, undefined);
            exercise1.id = 6;

            comp.activeExamPage.set(new ExamPage());
            comp.activeExamPage().exercise = exercise1;

            comp.studentExam.set(new StudentExam());
            comp.studentExam().exercises = [exercise0, exercise1];

            expect(comp.activePageIndex).toBe(1);
        });
    });

    it('should return the index of the active page', () => {
        const exercise0 = new QuizExercise(undefined, undefined);
        exercise0.id = 5;
        const exercise1 = new ProgrammingExercise(undefined, undefined);
        exercise1.id = 6;

        comp.activeExamPage.set(new ExamPage());
        comp.activeExamPage().exercise = exercise1;

        comp.studentExam.set(new StudentExam());
        comp.studentExam().exercises = [exercise0, exercise1];

        expect(comp.activePageIndex).toBe(1);
    });

    it('should set Exam Layout if the exam is started', () => {
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const examLayoutStub = vi.spyOn(examParticipationService, 'setExamLayout');
        const exercise0 = new QuizExercise(undefined, undefined);
        exercise0.id = 5;
        const exercise1 = new ProgrammingExercise(undefined, undefined);
        exercise1.id = 6;
        comp.ngOnInit();
        // after ngOnInit: its route emission resets whatever the previously displayed exam left behind, so the exam
        // under test is installed afterwards, exactly as a real load does
        comp.exam.set(new Exam());
        comp.exam().startDate = dayjs().subtract(1, 'hours');
        const studentExam = new StudentExam();
        studentExam.exercises = [exercise0, exercise1];
        comp.examStarted(studentExam);
        expect(examLayoutStub).toHaveBeenCalledOnce();
    });

    it('should reset Exam Layout if the summary is loaded and displayed', () => {
        const examLayoutStub = vi.spyOn(examParticipationService, 'resetExamLayout');

        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        studentExam.id = 3;
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.id = 3;
        studentExamWithExercises.exam = new Exam();
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '3' });
        vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();

        comp.testExam.set(false);
        comp.loadAndDisplaySummary();

        expect(examLayoutStub).toHaveBeenCalledOnce();
    });

    it('should not reset Exam Layout in loadAndDisplaySummary if it is a test exam', () => {
        const examLayoutStub = vi.spyOn(examParticipationService, 'resetExamLayout');
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(2000, 'seconds');
        studentExam.workingTime = 100;
        studentExam.id = 3;
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.id = 3;
        studentExamWithExercises.exam = new Exam();
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '3' });
        vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        comp.ngOnInit();

        comp.testExam.set(true);
        comp.loadAndDisplaySummary();

        expect(examLayoutStub).not.toHaveBeenCalledOnce();
    });

    it('should not load the summary in handleStudentExam when it is not yet visible', () => {
        const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary');
        vi.spyOn(comp, 'isOver').mockReturnValue(true);
        vi.spyOn(comp, 'isExamSummaryVisible').mockReturnValue(false);
        const studentExam = new StudentExam();
        studentExam.submitted = true;
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(1, 'hours');

        comp.handleStudentExam(studentExam);

        expect(summarySpy).not.toHaveBeenCalled();
        expect(comp.showExamSummary()).toBe(false);
        expect(comp.loadingExam()).toBe(false);
    });

    it('should load the summary in handleStudentExam when it is visible', () => {
        vi.spyOn(examParticipationService, 'resetExamLayout');
        const studentExamWithExercises = new StudentExam();
        studentExamWithExercises.exam = new Exam();
        const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExamWithExercises));
        vi.spyOn(comp, 'isOver').mockReturnValue(true);
        vi.spyOn(comp, 'isExamSummaryVisible').mockReturnValue(true);
        const studentExam = new StudentExam();
        studentExam.id = 3;
        studentExam.submitted = true;
        studentExam.exam = new Exam();
        studentExam.exam.startDate = dayjs().subtract(1, 'hours');

        comp.handleStudentExam(studentExam);

        expect(summarySpy).toHaveBeenCalledOnce();
        expect(comp.showExamSummary()).toBe(true);
    });

    describe('failed summary load', () => {
        const summaryError = () => throwError(() => new HttpErrorResponse({ status: 500 }));

        it('should withhold the summary and offer a retry when loadAndDisplaySummary fails', () => {
            const studentExam = new StudentExam();
            studentExam.id = 3;
            studentExam.exam = new Exam();
            comp.exam.set(studentExam.exam);
            comp.studentExam.set(studentExam);
            comp.loadingExam.set(true);
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError());

            comp.loadAndDisplaySummary();

            expect(comp.summaryLoadFailed()).toBe(true);
            expect(comp.showExamSummary()).toBe(false);
            expect(comp.loadingExam()).toBe(false);
        });

        it('should not substitute the cached exam when loadAndDisplaySummary fails', () => {
            const cachedStudentExam = new StudentExam();
            cachedStudentExam.id = 3;
            cachedStudentExam.exam = new Exam();
            comp.exam.set(cachedStudentExam.exam);
            comp.studentExam.set(cachedStudentExam);
            const localStorageSpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConductionFromLocalStorage');
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError());

            comp.loadAndDisplaySummary();

            expect(localStorageSpy).not.toHaveBeenCalled();
            expect(comp.showExamSummary()).toBe(false);
        });

        it('should show the retryable error state instead of the no-student-exam state when the initial test exam summary load fails', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            setRouteStudentExamId(activatedRoute, '3');
            activatedRoute.params = of({ courseId: '1', examId: '2' });
            const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError());
            const handleNoStudentExamSpy = vi.spyOn(comp, 'handleNoStudentExam');

            comp.ngOnInit();

            expect(summarySpy).toHaveBeenCalledOnce();
            expect(handleNoStudentExamSpy).not.toHaveBeenCalled();
            expect(comp.summaryLoadFailed()).toBe(true);
            expect(comp.loadingExam()).toBe(false);
        });

        it('should display the summary when the retry succeeds', () => {
            const studentExam = new StudentExam();
            studentExam.id = 3;
            studentExam.exam = new Exam();
            comp.exam.set(studentExam.exam);
            comp.studentExam.set(studentExam);
            const studentExamWithExercises = new StudentExam();
            studentExamWithExercises.id = 3;
            studentExamWithExercises.exam = new Exam();
            const summarySpy = vi
                .spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary')
                .mockReturnValueOnce(summaryError())
                .mockReturnValueOnce(of(studentExamWithExercises));

            comp.loadAndDisplaySummary();
            expect(comp.summaryLoadFailed()).toBe(true);

            comp.retryLoadSummary();

            expect(summarySpy).toHaveBeenCalledTimes(2);
            expect(comp.summaryLoadFailed()).toBe(false);
            expect(comp.showExamSummary()).toBe(true);
            expect(comp.studentExam()).toEqual(studentExamWithExercises);
            expect(comp.loadingExam()).toBe(false);
        });

        it('should render a retryable error message instead of the summary', () => {
            // let Angular run its own first change detection (which re-runs ngOnInit) before arranging the state under test
            fixture.changeDetectorRef.detectChanges();

            const studentExam = new StudentExam();
            studentExam.id = 3;
            studentExam.submitted = true;
            studentExam.exam = new Exam();
            comp.exam.set(studentExam.exam);
            comp.studentExam.set(studentExam);
            const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError());

            comp.loadAndDisplaySummary();
            fixture.changeDetectorRef.detectChanges();

            expect(comp.loadingExam()).toBe(false);
            expect(comp.isExamSummaryVisible()).toBe(true);
            expect(fixture.debugElement.query(By.css('#summaryLoadFailedMessage'))).not.toBeNull();
            expect(fixture.debugElement.query(By.css('jhi-exam-participation-summary'))).toBeNull();
            // the submission hint carries its own summary button, which would compete with the retry
            expect(fixture.debugElement.query(By.css('#showExamSummaryButton'))).toBeNull();

            const retryButton = fixture.debugElement.query(By.css('#retryLoadSummaryButton'));
            expect(retryButton).not.toBeNull();

            summarySpy.mockClear();
            retryButton.nativeElement.click();
            expect(summarySpy).toHaveBeenCalledOnce();
        });

        it('should clear the error state when the reused component navigates to another exam', () => {
            // The component instance is reused when only the :examId route parameter changes, so a failure on one exam
            // must not leave its error message on the next one
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const summaryError = throwError(() => new HttpErrorResponse({ status: 500 }));
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError);
            setRouteStudentExamId(activatedRoute, '3');
            comp.ngOnInit();

            // exam A: a test exam whose summary fails
            params.next({ courseId: '1', examId: '2' });
            expect(comp.summaryLoadFailed()).toBe(true);

            // exam B: still active, so it takes the regular loading branch and never touches the summary
            const ongoingStudentExam = new StudentExam();
            ongoingStudentExam.id = 9;
            ongoingStudentExam.exam = new Exam();
            ongoingStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            ongoingStudentExam.workingTime = 3600;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(ongoingStudentExam));
            // the route no longer carries a studentExamId, so the component itself has to drop the test exam state of exam A
            setRouteStudentExamId(activatedRoute, undefined);
            params.next({ courseId: '1', examId: '7' });

            expect(comp.testExam()).toBe(false);
            expect(comp.studentExamId()).toBeUndefined();
            expect(comp.summaryLoadFailed()).toBe(false);
            comp.retryLoadSummary();
            expect(examParticipationService.loadStudentExamWithExercisesForSummary).toHaveBeenCalledTimes(1);
        });

        it('should not keep the previous exam on screen while the next one is still loading', () => {
            // The conduction view is gated on exam()/studentExam() alone, so an exam left behind by the previous route
            // keeps rendering over the exam that is currently loading.
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const activeStudentExam = new StudentExam();
            activeStudentExam.id = 3;
            activeStudentExam.exam = new Exam();
            activeStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            activeStudentExam.workingTime = 3600;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(activeStudentExam));
            setRouteStudentExamId(activatedRoute, undefined);
            comp.ngOnInit();

            // exam A: loaded and started by the student
            params.next({ courseId: '1', examId: '2' });
            comp.examStartConfirmed.set(true);
            expect(comp.exam()).toBeDefined();

            // exam B: its request never completes, so nothing may be rendered for it yet
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(new Subject<StudentExam>().asObservable());
            params.next({ courseId: '1', examId: '7' });

            expect(comp.exam()).toBeUndefined();
            expect(comp.studentExam()).toBeUndefined();
            expect(comp.examStartConfirmed()).toBe(false);
        });

        it('should display the summary of a submitted test exam without requesting it a second time', () => {
            // A test exam is loaded through the summary endpoint already, and isExamSummaryPublished never gates a test
            // exam while isOver() is true once it is submitted, so handleStudentExam used to repeat the very same GET.
            // A transient failure of that repeat now replaces a summary that had already loaded with the error state,
            // so the redundant request has to go.
            const activatedRoute = TestBed.inject(ActivatedRoute);
            setRouteStudentExamId(activatedRoute, '3');
            activatedRoute.params = of({ courseId: '1', examId: '2' });
            const submittedTestExam = new StudentExam();
            submittedTestExam.id = 3;
            submittedTestExam.submitted = true;
            submittedTestExam.exam = new Exam();
            submittedTestExam.exam.testExam = true;
            const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(submittedTestExam));

            comp.ngOnInit();

            expect(summarySpy).toHaveBeenCalledOnce();
            expect(summarySpy).toHaveBeenCalledWith(1, 2, 3);
            expect(comp.showExamSummary()).toBe(true);
            expect(comp.summaryLoadFailed()).toBe(false);
            expect(comp.loadingExam()).toBe(false);
        });

        it('should stop the conduction work of the previous exam when navigating after it was started', () => {
            // Clearing the signals is not enough: the autosave timer and the live-event subscriptions of the started
            // exam keep running against the next one, and a second startAutoSaveTimer would overwrite the only handle
            // to the previous interval, leaving it ticking past even ngOnDestroy.
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const activeStudentExam = new StudentExam();
            activeStudentExam.id = 3;
            activeStudentExam.exam = new Exam();
            activeStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            activeStudentExam.workingTime = 3600;
            activeStudentExam.exercises = [];
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(activeStudentExam));
            setRouteStudentExamId(activatedRoute, undefined);
            comp.ngOnInit();

            // exam A: loaded and started, so its autosave timer and subscriptions are live
            params.next({ courseId: '1', examId: '2' });
            comp.examStarted(activeStudentExam);
            const previousInterval = comp.autoSaveInterval;
            const previousWorkingTimeSubscription = comp.workingTimeUpdateEventsSubscription;
            const previousProgrammingSubscription = new Subject<void>().subscribe();
            comp['programmingSubmissionSubscriptions'].push(previousProgrammingSubscription);
            expect(previousInterval).toBeDefined();
            const clearIntervalSpy = vi.spyOn(window, 'clearInterval');

            // exam B: its request never completes, so nothing of exam A may still be running
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(new Subject<StudentExam>().asObservable());
            params.next({ courseId: '1', examId: '7' });

            expect(clearIntervalSpy).toHaveBeenCalledWith(previousInterval);
            expect(comp.autoSaveInterval).toBeUndefined();
            expect(previousWorkingTimeSubscription?.closed).toBe(true);
            expect(comp.workingTimeUpdateEventsSubscription).toBeUndefined();
            expect(previousProgrammingSubscription.closed).toBe(true);
            expect(comp['programmingSubmissionSubscriptions']).toEqual([]);
        });

        it('should not keep the previous exam on screen underneath a failed summary load', () => {
            // The failure message is rendered above the conduction view rather than instead of it, so an exam surviving
            // from the previous route would appear as the exam the error is about.
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const activeStudentExam = new StudentExam();
            activeStudentExam.id = 3;
            activeStudentExam.exam = new Exam();
            activeStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            activeStudentExam.workingTime = 3600;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(activeStudentExam));
            setRouteStudentExamId(activatedRoute, undefined);
            comp.ngOnInit();

            // exam A: an active exam the student is working on
            params.next({ courseId: '1', examId: '2' });
            expect(comp.exam()).toBeDefined();

            // exam B: a test exam whose initial summary load fails
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            setRouteStudentExamId(activatedRoute, '9');
            params.next({ courseId: '1', examId: '7' });

            expect(comp.summaryLoadFailed()).toBe(true);
            expect(comp.exam()).toBeUndefined();
            expect(comp.studentExam()).toBeUndefined();
        });

        it('should leave summary mode when the reused component navigates from a loaded summary to an active exam', () => {
            // A successful summary is the one piece of state no summary request resets, so without an explicit reset it
            // survives the navigation and renders the next exam's conduction data as if it were a finished summary.
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const summaryStudentExam = new StudentExam();
            summaryStudentExam.id = 3;
            summaryStudentExam.exam = new Exam();
            vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(summaryStudentExam));
            setRouteStudentExamId(activatedRoute, '3');
            comp.ngOnInit();

            // exam A: a test exam whose summary loads successfully
            params.next({ courseId: '1', examId: '2' });
            comp.loadAndDisplaySummary();
            expect(comp.showExamSummary()).toBe(true);

            // exam B: still active, so it takes the regular loading branch and never touches the summary
            const ongoingStudentExam = new StudentExam();
            ongoingStudentExam.id = 9;
            ongoingStudentExam.exam = new Exam();
            ongoingStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            ongoingStudentExam.workingTime = 3600;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(ongoingStudentExam));
            setRouteStudentExamId(activatedRoute, undefined);
            params.next({ courseId: '1', examId: '7' });

            expect(comp.showExamSummary()).toBe(false);
            fixture.detectChanges();
            expect(fixture.debugElement.query(By.css('jhi-exam-participation-summary'))).toBeNull();
        });

        it('should ignore a summary failure of the previous exam that arrives after another exam started loading', () => {
            // The summary request of exam A is still in flight when the route switches to exam B. Its failure must not
            // restore the error state on B, nor install a retry that would re-request with B's route parameters.
            const params = new Subject<{ [key: string]: string }>();
            const activatedRoute = TestBed.inject(ActivatedRoute);
            activatedRoute.params = params;
            const pendingSummary = new Subject<StudentExam>();
            const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(pendingSummary.asObservable());
            setRouteStudentExamId(activatedRoute, '3');
            comp.ngOnInit();

            // exam A: a test exam whose summary request never completes before the navigation
            params.next({ courseId: '1', examId: '2' });
            expect(summarySpy).toHaveBeenCalledWith(1, 2, 3);

            // exam B: still active, so it takes the regular loading branch
            const ongoingStudentExam = new StudentExam();
            ongoingStudentExam.id = 9;
            ongoingStudentExam.exam = new Exam();
            ongoingStudentExam.exam.startDate = dayjs().subtract(1, 'minutes');
            ongoingStudentExam.workingTime = 3600;
            vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(ongoingStudentExam));
            setRouteStudentExamId(activatedRoute, undefined);
            params.next({ courseId: '1', examId: '7' });

            summarySpy.mockClear();
            pendingSummary.error(new HttpErrorResponse({ status: 500 }));

            expect(comp.summaryLoadFailed()).toBe(false);
            comp.retryLoadSummary();
            expect(summarySpy).not.toHaveBeenCalled();
        });

        it('should repeat the initial test exam load on retry rather than the show-summary request', () => {
            const activatedRoute = TestBed.inject(ActivatedRoute);
            setRouteStudentExamId(activatedRoute, '3');
            activatedRoute.params = of({ courseId: '1', examId: '2' });
            const summarySpy = vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(summaryError());

            comp.ngOnInit();
            expect(comp.summaryLoadFailed()).toBe(true);
            summarySpy.mockClear();

            comp.retryLoadSummary();

            // the student exam id from the route is used again, not the id of an exam that never loaded
            expect(summarySpy).toHaveBeenCalledWith(1, 2, 3);
            expect(comp.summaryLoadFailed()).toBe(true);
        });
    });

    it('should reset Exam Layout in onExamEndConfirmed if it is a test exam', () => {
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const examLayoutStub = vi.spyOn(examParticipationService, 'resetExamLayout');
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.id = 3;

        vi.spyOn(examParticipationService, 'loadTestRunWithExercisesForConduction').mockReturnValue(of(studentExam));
        vi.spyOn(examParticipationService, 'submitStudentExam').mockReturnValue(of(undefined));
        vi.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForSummary').mockReturnValue(of(studentExam));
        examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        comp.ngOnInit();
        comp.testExam.set(true);
        comp.courseId.set(1);
        comp.examId.set(2);
        comp.studentExamId.set(3);
        comp.onExamEndConfirmed();

        expect(examLayoutStub).toHaveBeenCalledOnce();
    });

    it('should display exam bar and timer during working time', () => {
        const exercise0 = new QuizExercise(undefined, undefined);
        exercise0.id = 5;
        const exercise1 = new ProgrammingExercise(undefined, undefined);
        exercise1.id = 6;
        // let Angular run its own first change detection (which runs ngOnInit, whose route emission resets the state of
        // a previously displayed exam) before arranging the state under test
        fixture.changeDetectorRef.detectChanges();
        comp.exam.set(new Exam());
        comp.exam().startDate = dayjs().subtract(1, 'hours');
        comp.studentExam.set(new StudentExam());
        comp.studentExam().submitted = false;
        comp.studentExam().exercises = [exercise0, exercise1];
        comp.examStartConfirmed.set(true);
        comp.individualStudentEndDate.set(dayjs().add(1, 'hours'));
        comp.individualStudentEndDateWithGracePeriod.set(dayjs().add(1, 'hours').add(1, 'minutes'));
        vi.spyOn(comp, 'isVisible').mockReturnValue(true);
        vi.spyOn(comp, 'isActive').mockReturnValue(true);
        vi.spyOn(comp, 'isOver').mockReturnValue(false);
        comp.activeExamPage.set(new ExamPage());
        comp.activeExamPage().exercise = exercise1;
        vi.spyOn(comp, 'studentFailedToSubmit', 'get').mockReturnValue(false);

        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeTruthy();
        const examBarDebugElement = fixture.debugElement.query(By.css('jhi-exam-bar'));
        expect(examBarDebugElement).toBeTruthy();
    });

    it('should not display exam bar and timer when exam was not submitted', () => {
        vi.spyOn(comp, 'studentFailedToSubmit', 'get').mockReturnValue(true);

        fixture.changeDetectorRef.detectChanges();

        const examBarDebugElement = fixture.debugElement.query(By.directive(ExamBarComponent));
        expect(examBarDebugElement).toBeFalsy();
    });

    it('should get whether student failed to submit', () => {
        comp.studentExam.set(new StudentExam());
        comp.testRunId.set(1);

        expect(comp.studentFailedToSubmit).toBe(false);

        comp.testRunId.set(0);
        const startDate = dayjs();
        const now = dayjs();
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.exam().startDate = startDate.subtract(2, 'hours');
        comp.exam().testExam = false;
        comp.studentExam().workingTime = 3600;
        comp.exam().gracePeriod = 1;
        comp.studentExam().submitted = false;
        expect(comp.studentFailedToSubmit).toBe(true);
    });

    it('should get whether student failed to submit a TestExam', () => {
        comp.studentExam.set(new StudentExam());
        comp.testRunId.set(0);
        comp.exam().testExam = true;

        comp.studentExam().started = false;
        expect(comp.studentFailedToSubmit).toBe(false);

        comp.studentExam().started = true;
        comp.studentExam().startedDate = undefined;
        expect(comp.studentFailedToSubmit).toBe(false);

        const now = dayjs();
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.studentExam().startedDate = now.subtract(2, 'hours');
        comp.studentExam().workingTime = 3600;
        comp.exam().gracePeriod = 1;
        comp.studentExam().submitted = false;
        expect(comp.studentFailedToSubmit).toBe(true);

        comp.studentExam().startedDate = now.subtract(1, 'hours');
        comp.studentExam().workingTime = 3600;
        comp.exam().gracePeriod = 1;
        comp.studentExam().submitted = false;
        expect(comp.studentFailedToSubmit).toBe(false);
    });

    it('should initialize individualStudentEndDateWithGracePeriod', () => {
        let now = dayjs();
        comp.studentExam.set(new StudentExam());

        // Case test run
        comp.studentExam().workingTime = 1;
        comp.exam().gracePeriod = 1;
        comp.exam().startDate = now;
        comp.studentExam().testRun = true;
        comp.initIndividualEndDates(now);

        expect(comp.individualStudentEndDateWithGracePeriod()).toEqual(now.add(1, 'seconds').add(1, 'seconds'));

        // Case test exam
        now = dayjs();
        comp.studentExam().workingTime = 1;
        comp.exam().testExam = true;
        comp.exam().gracePeriod = 1;
        comp.exam().startDate = dayjs().subtract(4, 'hours');

        comp.initIndividualEndDates(now);

        expect(comp.individualStudentEndDateWithGracePeriod()).toEqual(now.add(1, 'seconds').add(1, 'seconds'));
    });
});
