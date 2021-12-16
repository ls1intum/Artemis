import { HttpClientModule } from '@angular/common/http';
import { EventEmitter } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamParticipationCoverComponent } from 'app/exam/participate/exam-cover/exam-participation-cover.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockCourseManagementService } from '../../../helpers/mocks/service/mock-course-management.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockExamParticipationService } from '../../../helpers/mocks/service/mock-exam-participation.service';
import { MockArtemisServerDateService } from '../../../helpers/mocks/service/mock-server-date.service';

describe('ExamParticipationCoverComponent', () => {
    const course = { id: 456 } as Course;
    const exam: Exam = new Exam();
    exam.course = course;
    exam.id = 123;
    const studentExam: StudentExam = new StudentExam();
    studentExam.testRun = false;

    let component: ExamParticipationCoverComponent;
    let fixture: ComponentFixture<ExamParticipationCoverComponent>;
    let examParticipationService: ExamParticipationService;
    let accountService: AccountService;
    let artemisServerDateService: ArtemisServerDateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientModule, FormsModule],
            declarations: [
                ExamParticipationCoverComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(AlertErrorComponent),
                MockComponent(ExamTimerComponent),
                MockComponent(ExamInformationComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                MockProvider(ArtemisMarkdownService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ExamParticipationService, useClass: MockExamParticipationService },
                { provide: ArtemisServerDateService, useClass: MockArtemisServerDateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamParticipationCoverComponent);
                component = fixture.componentInstance;
                examParticipationService = TestBed.inject(ExamParticipationService);
                accountService = TestBed.inject(AccountService);
                artemisServerDateService = TestBed.inject(ArtemisServerDateService);

                component.startView = false;
                component.exam = exam;
                component.studentExam = studentExam;
                component.handInEarly = false;
                component.handInPossible = true;
                component.testRunStartTime = undefined;
            });
    });

    afterEach(() => {
        component.ngOnDestroy();
        jest.clearAllMocks();
    });

    it('should initialize with ngOnInit', fakeAsync(() => {
        const user = { name: 'admin' } as User;
        jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

        let now = dayjs();
        component.studentExam.workingTime = 1;
        component.exam.gracePeriod = 1;
        component.exam.startDate = now;

        component.ngOnInit();
        tick();

        expect(component.graceEndDate).toEqual(now.add(1, 'seconds').add(1, 'seconds'));
        expect(component.accountName).toBe(user.name);

        now = dayjs();
        component.startView = true;
        component.exam.startDate = now;
        component.ngOnInit();

        now = dayjs();
        component.studentExam.workingTime = 1;
        component.exam.gracePeriod = 1;
        component.testRunStartTime = now;
        component.studentExam.testRun = true;
        component.ngOnInit();
        expect(component.graceEndDate).toEqual(now.add(1, 'seconds').add(1, 'seconds'));
    }));

    it('should update confirmation', () => {
        fixture.detectChanges();

        component.startView = true;
        component.updateConfirmation();
        expect(component.startEnabled).toBe(false);

        component.startView = false;
        component.updateConfirmation();
        expect(component.endEnabled).toBe(false);
    });

    it('should start exam', fakeAsync(() => {
        jest.useFakeTimers();
        component.testRun = true;
        const exercise = { id: 99, type: ExerciseType.MODELING } as Exercise;
        component.studentExam.exercises = [exercise];
        const saveStudentExamSpy = jest.spyOn(examParticipationService, 'saveStudentExamToLocalStorage');

        component.startExam();

        expect(saveStudentExamSpy).toHaveBeenCalledTimes(1);
        expect(saveStudentExamSpy).toHaveBeenCalledWith(exam!.course!.id, exam!.id, studentExam);

        component.testRun = false;
        jest.spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').mockReturnValue(of(studentExam));
        component.exam.startDate = dayjs().subtract(1, 'days');

        component.startExam();
        tick();
        expect(component.studentExam).toEqual(studentExam);

        component.testRun = false;
        const startDate = dayjs();
        const now = dayjs();
        component.exam.startDate = startDate.add(1, 'hours');
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(component.waitingForExamStart).toBe(true);
        const difference = Math.ceil(component.exam.startDate.diff(now, 'seconds') / 60);
        expect(component.timeUntilStart).toBe(difference + ' min');

        component.exam.startDate = undefined;
        component.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(component.waitingForExamStart).toBe(true);
        expect(component.timeUntilStart).toBe('');
    }));

    it('test run should always have already started', () => {
        component.testRun = true;
        expect(component.hasStarted()).toBe(true);
    });

    it('should update displayed times if exam suddenly started ', () => {
        component.testRun = true;
        component.exam.startDate = dayjs();
        component.onExamStarted = new EventEmitter<StudentExam>();
        const eventSpy = jest.spyOn(component.onExamStarted, 'emit');

        component.updateDisplayedTimes(studentExam);
        expect(eventSpy).toHaveBeenCalledTimes(1);
    });

    it('should create the relative time text correctly', () => {
        let result = component.relativeTimeText(100);
        expect(result).toBe('1 min 40 s');
        result = component.relativeTimeText(10);
        expect(result).toBe('10 s');
    });

    it('should submit exam', () => {
        component.onExamEnded = new EventEmitter<StudentExam>();
        const saveStudentExamSpy = jest.spyOn(component.onExamEnded, 'emit');
        component.submitExam();
        expect(saveStudentExamSpy).toHaveBeenCalledTimes(1);
    });

    it('should continue after handing in early', () => {
        component.onExamContinueAfterHandInEarly = new EventEmitter<void>();
        const saveStudentExamSpy = jest.spyOn(component.onExamContinueAfterHandInEarly, 'emit');
        component.continueAfterHandInEarly();
        expect(saveStudentExamSpy).toHaveBeenCalledTimes(1);
    });

    it('should get start button enabled and end button enabled', () => {
        fixture.detectChanges();
        component.testRun = true;
        expect(component.startButtonEnabled).toBe(false);

        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.testRun = false;
        component.enteredName = 'admin';
        component.accountName = 'admin';
        component.confirmed = true;
        component.exam.visibleDate = dayjs().subtract(1, 'hours');
        expect(component.startButtonEnabled).toBe(true);

        component.handInPossible = true;
        expect(component.endButtonEnabled).toBe(true);
    });

    it('should get end button enabled', () => {
        component.enteredName = 'admin';
        expect(component.inserted).toBe(true);
    });

    it('should disable exam button', () => {
        component.ngOnInit();
        component.testRun = false;
        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.enteredName = 'user';
        component.accountName = 'user';
        component.confirmed = true;
        component.exam.visibleDate = dayjs().subtract(1, 'hours');
        component.exam.visibleDate = dayjs().add(1, 'hours');
        expect(component.startButtonEnabled).toBe(false);
    });

    it('should get whether student failed to submit', () => {
        component.testRun = true;
        expect(component.studentFailedToSubmit).toBe(false);

        component.testRun = false;
        const startDate = dayjs();
        const now = dayjs();
        jest.spyOn(artemisServerDateService, 'now').mockReturnValue(now);
        component.exam.startDate = startDate.subtract(2, 'hours');
        component.studentExam.workingTime = 3600;
        component.exam.gracePeriod = 1;
        component.studentExam.submitted = false;
        expect(component.studentFailedToSubmit).toBe(true);
    });
});
