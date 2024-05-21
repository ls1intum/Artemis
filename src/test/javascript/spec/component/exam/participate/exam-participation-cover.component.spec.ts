import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ExamParticipationCoverComponent } from 'app/exam/participate/exam-cover/exam-participation-cover.component';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ExamResultSummaryComponent } from 'app/exam/participate/summary/exam-result-summary.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
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
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { ExamLiveEvent, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { MockExamParticipationLiveEventsService } from '../../../helpers/mocks/service/mock-exam-participation-live-events.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { ExamGeneralInformationComponent } from 'app/exam/participate/general-information/exam-general-information.component';
import { FormsModule } from '@angular/forms';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ProfileService } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from '../../../../../../main/webapp/app/shared/layouts/profiles/profile-info.model';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';

describe('ExamParticipationCoverComponent', () => {
    let fixture: ComponentFixture<ExamParticipationCoverComponent>;
    let comp: ExamParticipationCoverComponent;
    let examParticipationService: ExamParticipationService;
    let artemisServerDateService: ArtemisServerDateService;
    let examParticipationLiveEventsService: ExamParticipationLiveEventsService;
    let courseService: CourseManagementService;
    let courseStorageService: CourseStorageService;
    let accountService: AccountService;
    let profileService: ProfileService;
    let getProfileInfoMock: jest.SpyInstance;

    function setupActivatedRouteMock() {
        const activatedRouteMock = {
            parent: {
                parent: {
                    parent: {
                        params: of({}),
                    },
                    params: of({ courseId: '1' }), // Second deepest parent having courseId
                },
                params: of({}),
            },
            params: of({ examId: '2' }), // Direct route params
        };
        return activatedRouteMock;
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ExamParticipationCoverComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamExerciseOverviewPageComponent),
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
                MockPipe(ArtemisDatePipe),
                MockComponent(ExamLiveEventsButtonComponent),
                MockComponent(ExamGeneralInformationComponent),
            ],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: ActivatedRoute,
                    useValue: setupActivatedRouteMock(),
                },
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
                fixture = TestBed.createComponent(ExamParticipationCoverComponent);
                comp = fixture.componentInstance;
                examParticipationService = TestBed.inject(ExamParticipationService);
                accountService = TestBed.inject(AccountService);
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);
                examParticipationLiveEventsService = TestBed.inject(ExamParticipationLiveEventsService);
                courseService = TestBed.inject(CourseManagementService);
                courseStorageService = TestBed.inject(CourseStorageService);
                fixture.detectChanges();
                comp.exam = new Exam();
                // mock profileService
                profileService = fixture.debugElement.injector.get(ProfileService);
                getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                const profileInfo = { inProduction: false } as ProfileInfo;
                const profileInfoSubject = new BehaviorSubject<ProfileInfo | null>(profileInfo);
                getProfileInfoMock.mockReturnValue(profileInfoSubject);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        comp.ngOnDestroy();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(ExamParticipationCoverComponent).toBeTruthy();
    });

    it('should update confirmation', () => {
        fixture.detectChanges();
        comp.updateConfirmation();
        expect(comp.startEnabled).toBeFalse();
    });

    it('should disable exam button', () => {
        comp.ngOnInit();
        comp.testRun = false;
        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.enteredName = 'user';
        comp.accountName = 'user';
        comp.confirmed = true;
        comp.exam.visibleDate = dayjs().subtract(1, 'hours');
        comp.exam.visibleDate = dayjs().add(1, 'hours');
        expect(comp.startButtonEnabled).toBeFalse();
    });

    it('should update displayed times if exam suddenly started', () => {
        const studentExam = new StudentExam();
        comp.testRun = true;
        comp.exam.startDate = dayjs();
        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        comp.updateDisplayedTimes(studentExam);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it('should initialize with correct account name', fakeAsync(() => {
        const user = { name: 'admin' } as User;
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();

        jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
        comp.ngOnInit();
        tick();
        expect(comp.accountName).toBe(user.name);
    }));

    describe('ExamParticipationSummaryComponent for TestRuns', () => {
        it('should initialize and display test run ribbon', () => {
            TestBed.inject(ActivatedRoute).params = of({ examId: '2', testRunId: '3' });
            comp.ngOnInit();
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

    it('should load test run if test run id is defined', () => {
        TestBed.inject(ActivatedRoute).params = of({ examId: '2', testRunId: '3' });
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
        const studentExamWithExercises = { id: 1, numberOfExamSessions: 0 };
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
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
        expect(loadStudentExamSpy).toHaveBeenCalledTimes(2);
        expect(loadStudentExamWithExercisesForSummary).toHaveBeenCalledOnce();
        expect(comp.studentExam).toEqual(studentExamWithExercises);
        expect(comp.studentExam).not.toEqual(studentExam);
    });

    it('should load new testExam if studentExam id is start', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        studentExam.exam.testExam = true;
        studentExam.exam.course = new Course();
        studentExam.workingTime = 100;
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: 'start' });
        const loadTestRunStub = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
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
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
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
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
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
        const loadStudentExamStub = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));

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

    it('should determine tutor status if no exam was loaded', () => {
        const httpError = new HttpErrorResponse({
            error: { errorKey: 'No student exam for you' },
            status: 400,
        });
        const course: Course = { isAtLeastTutor: true };

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '4' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => httpError));
        const courseStorageServiceSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(courseStorageServiceSpy).toHaveBeenCalledOnce();
        expect(comp.isAtLeastTutor).toBeTrue();
    });

    it('should determine tutor status if no exam was loaded and course was not cached', () => {
        const httpError = new HttpErrorResponse({
            error: { errorKey: 'No student exam for you' },
            status: 400,
        });
        const course: Course = { isAtLeastTutor: true };

        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2', studentExamId: '4' });
        const loadStudentExamSpy = jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(throwError(() => httpError));
        const courseStorageServiceSpy = jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(undefined);
        const courseServiceSpy = jest.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        comp.ngOnInit();
        expect(loadStudentExamSpy).toHaveBeenCalledOnce();
        expect(courseStorageServiceSpy).toHaveBeenCalledOnce();
        expect(courseServiceSpy).toHaveBeenCalledOnce();
        expect(comp.isAtLeastTutor).toBeTrue();
    });

    it('should start exam', fakeAsync(() => {
        jest.useFakeTimers();

        const studentExam = new StudentExam();
        const exam = new Exam();
        exam.id = 2;
        exam.course = new Course();
        exam.course.id = 1;
        studentExam.exam = exam;
        comp.testRun = true;
        const exercise = { id: 99, type: ExerciseType.MODELING } as Exercise;
        studentExam.exercises = [exercise];
        comp.studentExam = studentExam;
        comp.exam = studentExam.exam;
        const saveStudentExamSpy = jest.spyOn(examParticipationService, 'saveStudentExamToLocalStorage');

        comp.startExam();

        expect(saveStudentExamSpy).toHaveBeenCalledOnce();
        expect(saveStudentExamSpy).toHaveBeenCalledWith(exam!.course!.id, exam!.id, studentExam);

        comp.testRun = false;
        jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(studentExam));
        comp.exam.startDate = dayjs().subtract(1, 'days');

        comp.startExam();
        tick();
        expect(comp.studentExam).toEqual(studentExam);

        comp.testRun = false;
        const startDate = dayjs();
        const now = dayjs();
        comp.exam.startDate = startDate.add(1, 'hours');
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(comp.waitingForExamStart).toBeTrue();
        const difference = Math.ceil(comp.exam.startDate.diff(now, 'seconds') / 60);
        expect(comp.timeUntilStart).toBe(difference + ' min');

        comp.exam.startDate = undefined;
        comp.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(comp.waitingForExamStart).toBeTrue();
        expect(comp.timeUntilStart).toBe('');

        // Case test exam
        comp.testRun = false;
        comp.testExam = true;
        comp.exam.testExam = true;
        const exercise1 = { id: 87, type: ExerciseType.TEXT } as Exercise;
        comp.studentExam.exercises = [exercise1];

        jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(studentExam));

        comp.exam.startDate = dayjs().subtract(1, 'days');

        comp.startExam();
        tick();
        expect(comp.studentExam).toEqual(studentExam);

        const startDate1 = dayjs();
        const now1 = dayjs();
        comp.exam.startDate = startDate1.add(2, 'hours');
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now1);
        comp.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(comp.waitingForExamStart).toBeTrue();
        const difference1 = Math.ceil(comp.exam.startDate.diff(now1, 's') / 60);
        expect(comp.timeUntilStart).toBe(difference1 + ' min');

        comp.exam.startDate = undefined;
        comp.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(comp.waitingForExamStart).toBeTrue();
        expect(comp.timeUntilStart).toBe('');
    }));

    it('should redirect to exam participation after start', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        comp.testRun = false;
        jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        jest.spyOn(examParticipationService, 'lastSaveFailed').mockReturnValue(false);
        comp.ngOnInit();
        expect(comp.loadingExam).toBeFalse();

        const router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(studentExam));

        const startDate = dayjs().subtract(1, 'days');
        const date = dayjs();
        comp.exam.startDate = startDate;
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(date);

        comp.startExam();
        expect(navigateSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'exams', 2, 'participation']);
    });

    it('should display exam summary if the student submitted the exam', () => {
        const studentExam = new StudentExam();
        studentExam.exam = new Exam();
        comp.testRun = false;
        studentExam.submitted = true;

        jest.spyOn(examParticipationService, 'getOwnStudentExam').mockReturnValue(of(studentExam));
        const loadExamSummaryStub = jest.spyOn(comp, 'loadAndDisplaySummary');

        comp.ngOnInit();
        expect(loadExamSummaryStub).toHaveBeenCalledOnce();
    });

    it('test run should always have already started', () => {
        comp.testRun = true;
        expect(comp.hasStarted()).toBeTrue();
    });

    it('should create the relative time text correctly', () => {
        let result = comp.relativeTimeText(100);
        expect(result).toBe('1 min 40 s');
        result = comp.relativeTimeText(10);
        expect(result).toBe('10 s');
    });

    it('should get start button enabled and disabled', () => {
        fixture.detectChanges();
        comp.confirmed = false;
        comp.startEnabled = false;
        comp.testRun = true;
        expect(comp.startButtonEnabled).toBeFalse();

        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.testRun = false;
        comp.enteredName = 'admin';
        comp.accountName = 'admin';
        comp.confirmed = true;
        comp.exam.visibleDate = dayjs().subtract(1, 'hours');
        expect(comp.startButtonEnabled).toBeTrue();
    });

    it('should disable start button', () => {
        comp.confirmed = false;
        comp.startEnabled = false;
        comp.testRun = false;
        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.enteredName = 'user';
        comp.accountName = 'user';
        comp.confirmed = true;
        comp.exam.visibleDate = dayjs().subtract(1, 'hours');
        comp.exam.visibleDate = dayjs().add(1, 'hours');
        expect(comp.startButtonEnabled).toBeFalse();
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

    it('should get whether student failed to submit a TestExam', () => {
        comp.studentExam = new StudentExam();
        comp.studentExam.exam = comp.exam;
        comp.testRun = false;
        comp.exam.testExam = true;

        comp.studentExam.started = false;
        expect(comp.studentFailedToSubmit).toBeFalse();

        comp.studentExam.started = true;
        comp.studentExam.startedDate = undefined;
        expect(comp.studentFailedToSubmit).toBeFalse();

        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        comp.studentExam.startedDate = now.subtract(2, 'hours');
        comp.studentExam.workingTime = 3600;
        comp.exam.gracePeriod = 1;
        comp.studentExam.submitted = false;
        expect(comp.studentFailedToSubmit).toBeTrue();

        comp.studentExam.startedDate = now.subtract(1, 'hours');
        comp.studentExam.workingTime = 3600;
        comp.exam.gracePeriod = 1;
        comp.studentExam.submitted = false;
        expect(comp.studentFailedToSubmit).toBeFalse();
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

    const setComponentWithoutTestRun = () => {
        TestBed.inject(ActivatedRoute).params = of({ courseId: '1', examId: '2' });
        comp.ngOnInit();
        comp.exam = new Exam();
    };

    describe('isVisible', () => {
        it('should be visible if test run', () => {
            TestBed.inject(ActivatedRoute).params = of({ examId: '2', testRunId: '3' });
            comp.ngOnInit();
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
});
