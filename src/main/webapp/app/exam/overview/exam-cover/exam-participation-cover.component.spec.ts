import { EventEmitter } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam, ExamType } from 'app/exam/shared/entities/exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamParticipationCoverComponent } from 'app/exam/overview/exam-cover/exam-participation-cover.component';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamGeneralInformationComponent } from 'app/exam/overview/general-information/exam-general-information.component';
import { ExamTimerComponent } from 'app/exam/overview/timer/exam-timer.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockExamParticipationService } from 'test/helpers/mocks/service/mock-exam-participation.service';
import { MockArtemisServerDateService } from 'test/helpers/mocks/service/mock-server-date.service';
import { ExamLiveEventsButtonComponent } from 'app/exam/overview/events/button/exam-live-events-button.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamParticipationCoverComponent', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 456 } as Course;
    let exam: Exam;
    let studentExam: StudentExam;

    let component: ExamParticipationCoverComponent;
    let fixture: ComponentFixture<ExamParticipationCoverComponent>;
    let examParticipationService: ExamParticipationService;
    let accountService: AccountService;
    let artemisServerDateService: ArtemisServerDateService;

    /**
     * Helper that mutates the input objects (no setInput re-call), then forces
     * a re-render to flush effects.
     */
    function flushInputs() {
        fixture.detectChanges();
    }

    beforeEach(() => {
        exam = new Exam();
        exam.course = course;
        exam.id = 123;
        studentExam = new StudentExam();
        studentExam.testRun = false;
        studentExam.id = 1;

        TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                ExamParticipationCoverComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamTimerComponent),
                MockComponent(ExamGeneralInformationComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
                MockComponent(ExamLiveEventsButtonComponent),
            ],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                MockProvider(ArtemisMarkdownService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ExamParticipationCoverComponent);
        component = fixture.componentInstance;
        examParticipationService = TestBed.inject(ExamParticipationService);
        accountService = TestBed.inject(AccountService);
        artemisServerDateService = TestBed.inject(ArtemisServerDateService);

        fixture.componentRef.setInput('startView', false);
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.componentRef.setInput('handInEarly', false);
        fixture.componentRef.setInput('handInPossible', true);
        fixture.componentRef.setInput('testRunStartTime', undefined);
    });

    afterEach(() => {
        component.ngOnDestroy();
        vi.clearAllMocks();
    });

    it('should initialize accountName when identity resolves', async () => {
        const user = { name: 'admin' } as User;
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
        flushInputs();
        await Promise.resolve();
        expect(component.accountName()).toBe(user.name);
    });

    it('should update confirmation', () => {
        flushInputs();

        fixture.componentRef.setInput('startView', true);
        component.updateConfirmation();
        expect(component.startEnabled()).toBe(false);

        fixture.componentRef.setInput('startView', false);
        component.updateConfirmation();
        expect(component.endEnabled()).toBe(false);
    });

    it('should start exam', async () => {
        vi.useFakeTimers();
        const localStudentExam = new StudentExam();
        localStudentExam.testRun = true;
        localStudentExam.id = 1;
        const exercise = { id: 99, type: ExerciseType.MODELING } as Exercise;
        localStudentExam.exercises = [exercise];
        fixture.componentRef.setInput('studentExam', localStudentExam);

        const saveStudentExamSpy = vi.spyOn(examParticipationService, 'saveStudentExamToLocalStorage');

        component.startExam();

        expect(saveStudentExamSpy).toHaveBeenCalledOnce();
        expect(saveStudentExamSpy).toHaveBeenCalledWith(exam!.course!.id, exam!.id, localStudentExam);

        const localStudentExam2 = new StudentExam();
        localStudentExam2.testRun = false;
        localStudentExam2.id = 1;
        fixture.componentRef.setInput('studentExam', localStudentExam2);

        const examWithStartDate = new Exam();
        examWithStartDate.course = course;
        examWithStartDate.id = 123;
        examWithStartDate.startDate = dayjs().subtract(1, 'days');
        fixture.componentRef.setInput('exam', examWithStartDate);

        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(localStudentExam2));

        component.startExam();
        await Promise.resolve();
        expect(component.studentExam()).toEqual(localStudentExam2);

        const startDate = dayjs();
        const now = dayjs();
        const examFutureStart = new Exam();
        examFutureStart.course = course;
        examFutureStart.id = 123;
        examFutureStart.startDate = startDate.add(1, 'hours');
        fixture.componentRef.setInput('exam', examFutureStart);
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.startExam();
        await Promise.resolve();
        vi.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(component.waitingForExamStart()).toBe(true);
        const difference = Math.ceil(examFutureStart.startDate.diff(now, 'seconds') / 60);
        expect(component.timeUntilStart()).toBe(difference + ' min');

        const examNoStart = new Exam();
        examNoStart.course = course;
        examNoStart.id = 123;
        examNoStart.startDate = undefined;
        fixture.componentRef.setInput('exam', examNoStart);
        component.startExam();
        await Promise.resolve();
        vi.advanceTimersByTime(UI_RELOAD_TIME + 1);
        expect(component.waitingForExamStart()).toBe(true);
        expect(component.timeUntilStart()).toBe('');

        // Case test exam
        const testExamStudent = new StudentExam();
        testExamStudent.testRun = false;
        testExamStudent.id = 1;
        const exercise1 = { id: 87, type: ExerciseType.TEXT } as Exercise;
        testExamStudent.exercises = [exercise1];
        fixture.componentRef.setInput('studentExam', testExamStudent);

        const testExam = new Exam();
        testExam.course = course;
        testExam.id = 123;
        testExam.examType = ExamType.PRACTICE;
        testExam.startDate = dayjs().subtract(1, 'days');
        fixture.componentRef.setInput('exam', testExam);

        vi.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(testExamStudent));

        component.startExam();
        await Promise.resolve();
        expect(component.studentExam()).toEqual(testExamStudent);

        const startDate1 = dayjs();
        const now1 = dayjs();
        const testExamFuture = new Exam();
        testExamFuture.course = course;
        testExamFuture.id = 123;
        testExamFuture.examType = ExamType.PRACTICE;
        testExamFuture.startDate = startDate1.add(2, 'hours');
        fixture.componentRef.setInput('exam', testExamFuture);
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now1);
        component.startExam();
        await Promise.resolve();
        vi.advanceTimersByTime(UI_RELOAD_TIME + 1);
        expect(component.waitingForExamStart()).toBe(true);
        const difference1 = Math.ceil(testExamFuture.startDate.diff(now1, 's') / 60);
        expect(component.timeUntilStart()).toBe(difference1 + ' min');

        const testExamNoStart = new Exam();
        testExamNoStart.course = course;
        testExamNoStart.id = 123;
        testExamNoStart.examType = ExamType.PRACTICE;
        testExamNoStart.startDate = undefined;
        fixture.componentRef.setInput('exam', testExamNoStart);
        component.startExam();
        await Promise.resolve();
        vi.advanceTimersByTime(UI_RELOAD_TIME + 1);
        expect(component.waitingForExamStart()).toBe(true);
        expect(component.timeUntilStart()).toBe('');
    });

    it('test run should always have already started', () => {
        const localStudentExam = new StudentExam();
        localStudentExam.testRun = true;
        localStudentExam.id = 1;
        fixture.componentRef.setInput('studentExam', localStudentExam);
        expect(component.hasStarted()).toBe(true);
    });

    it('should update displayed times if exam suddenly started', () => {
        const localStudentExam = new StudentExam();
        localStudentExam.testRun = true;
        localStudentExam.id = 1;
        fixture.componentRef.setInput('studentExam', localStudentExam);

        const examWithDate = new Exam();
        examWithDate.course = course;
        examWithDate.id = 123;
        examWithDate.startDate = dayjs();
        fixture.componentRef.setInput('exam', examWithDate);

        (component as any).onExamStarted = new EventEmitter<StudentExam>();
        const eventSpy = vi.spyOn(component.onExamStarted, 'emit');

        component.updateDisplayedTimes(localStudentExam);
        expect(eventSpy).toHaveBeenCalledOnce();
    });

    it('should create the relative time text correctly', () => {
        let result = component.relativeTimeText(100);
        expect(result).toBe('1 min 40 s');
        result = component.relativeTimeText(10);
        expect(result).toBe('10 s');
    });

    it('should submit exam', () => {
        (component as any).onExamEnded = new EventEmitter<StudentExam>();
        const saveStudentExamSpy = vi.spyOn(component.onExamEnded, 'emit');
        component.submitExam();
        expect(saveStudentExamSpy).toHaveBeenCalledOnce();
    });

    it('should continue after handing in early', () => {
        (component as any).onExamContinueAfterHandInEarly = new EventEmitter<void>();
        const saveStudentExamSpy = vi.spyOn(component.onExamContinueAfterHandInEarly, 'emit');
        component.continueAfterHandInEarly();
        expect(saveStudentExamSpy).toHaveBeenCalledOnce();
    });

    it('should get start button enabled and end button enabled', () => {
        flushInputs();

        const trStudentExam = new StudentExam();
        trStudentExam.testRun = true;
        trStudentExam.id = 1;
        fixture.componentRef.setInput('studentExam', trStudentExam);
        expect(component.startButtonEnabled).toBe(false);

        const now = dayjs();
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);

        const realStudentExam = new StudentExam();
        realStudentExam.testRun = false;
        realStudentExam.id = 1;
        fixture.componentRef.setInput('studentExam', realStudentExam);

        component.enteredName = 'admin';
        component.accountName.set('admin');
        component.confirmed = true;

        const examVisible = new Exam();
        examVisible.course = course;
        examVisible.id = 123;
        examVisible.visibleDate = dayjs().subtract(1, 'hours');
        fixture.componentRef.setInput('exam', examVisible);
        expect(component.startButtonEnabled).toBe(true);

        fixture.componentRef.setInput('handInPossible', true);
        expect(component.endButtonEnabled).toBe(true);
    });

    it('should get end button enabled', () => {
        component.enteredName = 'admin';
        expect(component.inserted).toBe(true);
    });

    it('should disable exam button', () => {
        flushInputs();

        const realStudentExam = new StudentExam();
        realStudentExam.testRun = false;
        realStudentExam.id = 1;
        fixture.componentRef.setInput('studentExam', realStudentExam);

        const now = dayjs();
        vi.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.enteredName = 'user';
        component.accountName.set('user');
        component.confirmed = true;

        const examFuture = new Exam();
        examFuture.course = course;
        examFuture.id = 123;
        examFuture.visibleDate = dayjs().add(1, 'hours');
        fixture.componentRef.setInput('exam', examFuture);
        expect(component.startButtonEnabled).toBe(false);
    });
});
