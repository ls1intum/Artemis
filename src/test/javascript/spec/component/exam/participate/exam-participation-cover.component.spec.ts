import { HttpClientModule } from '@angular/common/http';
import { EventEmitter } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
import * as chai from 'chai';
import * as moment from 'moment';
import { JhiTranslateDirective } from 'ng-jhipster';
import { MockComponent } from 'ng-mocks';
import { MockDirective } from 'ng-mocks';
import { MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { spy } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../../helpers/mocks/service/mock-route.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

chai.use(sinonChai);
const expect = chai.expect;

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
                MockDirective(JhiTranslateDirective),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: MockRouter },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ExamParticipationCoverComponent);
        component = fixture.componentInstance;
        examParticipationService = TestBed.inject(ExamParticipationService);
        accountService = TestBed.inject(AccountService);
        artemisServerDateService = TestBed.inject(ArtemisServerDateService);
    });
    beforeEach(() => {
        component.startView = false;
        component.exam = exam;
        component.studentExam = studentExam;
        component.handInEarly = false;
        component.handInPossible = true;
        component.testRunStartTime = undefined;
    });

    afterEach(function () {
        component.ngOnDestroy();
        sinon.restore();
        jest.clearAllMocks();
    });

    it('should initialize with ngOnInit', fakeAsync(() => {
        const user = { name: 'admin' } as User;
        spyOn(accountService, 'identity').and.returnValue(Promise.resolve(user));

        let now = moment();
        component.studentExam.workingTime = 1;
        component.exam.gracePeriod = 1;
        component.exam.startDate = now;

        component.ngOnInit();
        tick();

        expect(component.graceEndDate).to.deep.equal(now.add(1, 'seconds').add(1, 'seconds'));
        expect(component.accountName).to.equal(user.name);

        now = moment();
        component.startView = true;
        component.exam.startDate = now;
        component.ngOnInit();

        now = moment();
        component.studentExam.workingTime = 1;
        component.exam.gracePeriod = 1;
        component.testRunStartTime = now;
        component.studentExam.testRun = true;
        component.ngOnInit();
        expect(component.graceEndDate).to.deep.equal(now.add(1, 'seconds').add(1, 'seconds'));
    }));

    it('should update confirmation', () => {
        fixture.detectChanges();

        component.startView = true;
        component.updateConfirmation();
        expect(component.startEnabled).to.be.false;

        component.startView = false;
        component.updateConfirmation();
        expect(component.endEnabled).to.be.false;
    });

    it('should start exam', fakeAsync(() => {
        jest.useFakeTimers();
        component.testRun = true;
        const exercise = { id: 99, type: ExerciseType.MODELING } as Exercise;
        component.studentExam.exercises = [exercise];
        const saveStudentExamSpy = spy(examParticipationService, 'saveStudentExamToLocalStorage');

        component.startExam();

        expect(saveStudentExamSpy).to.be.calledOnceWith(exam!.course!.id, exam!.id, studentExam);

        component.testRun = false;
        spyOn(examParticipationService, 'loadStudentExamWithExercisesForConduction').and.returnValue(of(studentExam));
        component.exam.startDate = moment().subtract(1, 'days');

        component.startExam();
        tick();
        expect(component.studentExam).to.deep.equal(studentExam);

        component.testRun = false;
        const startDate = moment();
        const now = moment();
        component.exam.startDate = startDate.add(1, 'hours');
        spyOn(artemisServerDateService, 'now').and.returnValue(now);
        component.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(component.waitingForExamStart).to.be.true;
        const difference = Math.ceil(component.exam.startDate.diff(now, 'seconds') / 60);
        expect(component.timeUntilStart).to.equal(difference + ' min');

        component.exam.startDate = undefined;
        component.startExam();
        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing
        expect(component.waitingForExamStart).to.be.true;
        expect(component.timeUntilStart).to.equal('');
    }));

    it('test run should always have already started', () => {
        component.testRun = true;
        expect(component.hasStarted()).to.be.true;
    });

    it('should update displayed times if exam suddenly started ', () => {
        component.testRun = true;
        component.exam.startDate = moment();
        component.onExamStarted = new EventEmitter<StudentExam>();
        const eventSpy = spy(component.onExamStarted, 'emit');

        component.updateDisplayedTimes(studentExam);
        expect(eventSpy).to.be.calledOnce;
    });

    it('should create the relative time text correctly', () => {
        let result = component.relativeTimeText(100);
        expect(result).to.equal('1 min 40 s');
        result = component.relativeTimeText(10);
        expect(result).to.equal('10 s');
    });

    it('should submit exam', () => {
        component.onExamEnded = new EventEmitter<StudentExam>();
        const saveStudentExamSpy = spy(component.onExamEnded, 'emit');
        component.submitExam();
        expect(saveStudentExamSpy).to.be.calledOnce;
    });

    it('should continue after handing in early', () => {
        component.onExamContinueAfterHandInEarly = new EventEmitter<void>();
        const saveStudentExamSpy = spy(component.onExamContinueAfterHandInEarly, 'emit');
        component.continueAfterHandInEarly();
        expect(saveStudentExamSpy).to.be.calledOnce;
    });

    it('should get start button enabled and end button enabled', () => {
        fixture.detectChanges();
        component.testRun = true;
        expect(component.startButtonEnabled).to.be.false;

        const now = moment();
        spyOn(artemisServerDateService, 'now').and.returnValue(now);
        component.testRun = false;
        component.enteredName = 'admin';
        component.accountName = 'admin';
        component.confirmed = true;
        component.exam.visibleDate = moment().subtract(1, 'hours');
        expect(component.startButtonEnabled).to.be.true;

        component.handInPossible = true;
        expect(component.endButtonEnabled).to.be.true;
    });

    it('should get end button enabled', () => {
        component.enteredName = 'admin';
        expect(component.inserted).to.be.true;
    });

    it('should disable exam button', () => {
        component.ngOnInit();
        component.testRun = false;
        const now = moment();
        spyOn(artemisServerDateService, 'now').and.returnValue(now);
        component.enteredName = 'user';
        component.accountName = 'user';
        component.confirmed = true;
        component.exam.visibleDate = moment().subtract(1, 'hours');
        component.exam.visibleDate = moment().add(1, 'hours');
        expect(component.startButtonEnabled).to.be.false;
    });

    it('should get whether student failed to submit', () => {
        component.testRun = true;
        expect(component.studentFailedToSubmit).to.be.false;

        component.testRun = false;
        const startDate = moment();
        const now = moment();
        spyOn(artemisServerDateService, 'now').and.returnValue(now);
        component.exam.startDate = startDate.subtract(2, 'hours');
        component.studentExam.workingTime = 3600;
        component.exam.gracePeriod = 1;
        component.studentExam.submitted = false;
        expect(component.studentFailedToSubmit).to.be.true;
    });
});
