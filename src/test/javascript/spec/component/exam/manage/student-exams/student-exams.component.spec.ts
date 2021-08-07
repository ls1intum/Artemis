import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockComponent, MockDirective, MockPipe, MockProvider, MockModule } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockLocalStorageService } from '../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { StudentExam } from 'app/entities/student-exam.model';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModule, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../helpers/mocks/service/mock-account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentExamsComponent', () => {
    let studentExamsComponentFixture: ComponentFixture<StudentExamsComponent>;
    let studentExamsComponent: StudentExamsComponent;
    let studentExams: StudentExam[] = [];
    let course: Course;
    let studentOne: User;
    let studentTwo: User;
    let studentExamOne: StudentExam | undefined;
    let studentExamTwo: StudentExam | undefined;
    let exam: Exam;
    let modalService: NgbModal;
    let examManagementService: ExamManagementService;

    const providers = [
        MockProvider(ExamManagementService, {
            find: () => {
                return of(
                    new HttpResponse({
                        body: exam,
                        status: 200,
                    }),
                );
            },
            assessUnsubmittedExamModelingAndTextParticipations: () => {
                return of(
                    new HttpResponse({
                        body: 1,
                        status: 200,
                    }),
                );
            },
            generateStudentExams: () => {
                return of(
                    new HttpResponse({
                        body: [studentExamOne!, studentExamTwo!],
                        status: 200,
                    }),
                );
            },
            generateMissingStudentExams: () => {
                return of(
                    new HttpResponse({
                        body: studentExamTwo ? [studentExamTwo] : [],
                        status: 200,
                    }),
                );
            },
            startExercises: () => {
                return of(
                    new HttpResponse({
                        body: 2,
                        status: 200,
                    }),
                );
            },
            unlockAllRepositories: () => {
                return of(
                    new HttpResponse({
                        body: 2,
                        status: 200,
                    }),
                );
            },
            lockAllRepositories: () => {
                return of(
                    new HttpResponse({
                        body: 2,
                        status: 200,
                    }),
                );
            },
            evaluateQuizExercises: () => {
                return of(
                    new HttpResponse({
                        body: 1,
                        status: 200,
                    }),
                );
            },
        }),
        MockProvider(StudentExamService, {
            findAllForExam: () => {
                return of(
                    new HttpResponse({
                        body: studentExams,
                        status: 200,
                    }),
                );
            },
        }),
        MockProvider(CourseManagementService, {
            find: () => {
                return of(
                    new HttpResponse({
                        body: course,
                        status: 200,
                    }),
                );
            },
        }),
        MockProvider(JhiAlertService),
        MockDirective(JhiTranslateDirective),
        {
            provide: LocalStorageService,
            useClass: MockLocalStorageService,
        },
        {
            provide: ActivatedRoute,
            useValue: {
                params: {
                    subscribe: (fn: (value: Params) => void) =>
                        fn({
                            courseId: 1,
                        }),
                },
                snapshot: {
                    paramMap: convertToParamMap({
                        courseId: '1',
                        examId: '1',
                    }),
                },
            },
        },
        { provide: AccountService, useClass: MockAccountService },
    ];

    beforeEach(() => {
        course = new Course();
        course.id = 1;

        studentOne = new User();
        studentOne.id = 1;

        studentTwo = new User();
        studentTwo.id = 2;

        exam = new Exam();
        exam.course = course;
        exam.id = 1;
        exam.registeredUsers = [studentOne, studentTwo];
        exam.endDate = moment();
        exam.startDate = exam.endDate.subtract(60, 'seconds');

        studentExamOne = new StudentExam();
        studentExamOne.exam = exam;
        studentExamOne.id = 1;
        studentExamOne.workingTime = 70;
        studentExamOne.user = studentOne;

        studentExamTwo = new StudentExam();
        studentExamTwo.exam = exam;
        studentExamTwo.id = 1;
        studentExamTwo.workingTime = 70;
        studentExamTwo.user = studentOne;

        studentExams = [studentExamOne, studentExamTwo];

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgbModule), NgxDatatableModule, FontAwesomeTestingModule, TranslateModule.forRoot()],
            declarations: [
                StudentExamsComponent,
                MockComponent(StudentExamStatusComponent),
                MockComponent(AlertComponent),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(DataTableComponent),
            ],
            providers,
        })
            .compileComponents()
            .then(() => {
                studentExamsComponentFixture = TestBed.createComponent(StudentExamsComponent);
                studentExamsComponent = studentExamsComponentFixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                examManagementService = TestBed.inject(ExamManagementService);
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        const courseManagementService = TestBed.inject(CourseManagementService);

        const studentExamService = TestBed.inject(StudentExamService);
        const findCourseSpy = sinon.spy(courseManagementService, 'find');
        const findExamSpy = sinon.spy(examManagementService, 'find');
        const findAllStudentExamsSpy = sinon.spy(studentExamService, 'findAllForExam');
        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponentFixture).to.be.ok;
        expect(findCourseSpy).to.have.been.calledOnce;
        expect(findExamSpy).to.have.been.calledOnce;
        expect(findAllStudentExamsSpy).to.have.been.calledOnce;
        expect(studentExamsComponent.course).to.deep.equal(course);
        expect(studentExamsComponent.studentExams).to.deep.equal(studentExams);
        expect(studentExamsComponent.exam).to.deep.equal(exam);
        expect(studentExamsComponent.hasStudentsWithoutExam).to.equal(false);
        expect(studentExamsComponent.longestWorkingTime).to.equal(studentExamOne!.workingTime);
        expect(studentExamsComponent.isExamOver).to.equal(false);
        expect(studentExamsComponent.isLoading).to.equal(false);
    });

    it('should not show assess unsubmitted student exam modeling and text participations', () => {
        // user is not an instructor
        studentExamsComponentFixture.detectChanges();
        const assessButton = studentExamsComponentFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).to.not.exist;
    });

    it('should disable show assess unsubmitted student exam modeling and text participations', () => {
        course.isAtLeastInstructor = true;

        // exam is not over
        studentExamsComponentFixture.detectChanges();
        const assessButton = studentExamsComponentFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).to.exist;
        expect(assessButton.nativeElement.disabled).to.equal(true);
    });

    it('should automatically assess modeling and text exercises of unsubmitted student exams', () => {
        studentExamOne!.workingTime = 10;
        exam.startDate = moment().subtract(200, 'seconds');
        exam.endDate = moment().subtract(100, 'seconds');
        exam.gracePeriod = 0;
        course.isAtLeastInstructor = true;

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamOver).to.equal(true);
        expect(course).to.exist;
        const assessSpy = sinon.spy(examManagementService, 'assessUnsubmittedExamModelingAndTextParticipations');
        const assessButton = studentExamsComponentFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).to.exist;
        assessButton.nativeElement.click();
        expect(assessSpy).to.have.been.calledOnce;
    });

    it('should correctly catch HTTPError when assessing unsubmitted exams', () => {
        const alertService = TestBed.inject(JhiAlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        studentExamOne!.workingTime = 10;
        exam.startDate = moment().subtract(200, 'seconds');
        exam.endDate = moment().subtract(100, 'seconds');
        exam.gracePeriod = 0;
        course.isAtLeastInstructor = true;

        studentExamsComponentFixture.detectChanges();
        const alertServiceSpy = sinon.spy(alertService, 'error');
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamOver).to.equal(true);
        expect(course).to.exist;
        const assessStub = sinon.stub(examManagementService, 'assessUnsubmittedExamModelingAndTextParticipations').returns(throwError(httpError));
        const assessButton = studentExamsComponentFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).to.exist;
        assessButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        assessStub.restore();
    });

    it('should generate student exams if there are none', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');

        studentExams = [];
        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamStarted).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;

        studentExams = [studentExamOne!, studentExamTwo!];

        const generateStudentExamsSpy = sinon.spy(examManagementService, 'generateStudentExams');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).to.exist;
        expect(generateStudentExamsButton.nativeElement.disabled).to.equal(false);
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).to.equal(false);
        generateStudentExamsButton.nativeElement.click();
        expect(generateStudentExamsSpy).to.have.been.calledOnce;
        expect(studentExamsComponent.studentExams.length).to.equal(2);
    });

    it('should correctly catch HTTPError and get additional error when generating student exams', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const alertService = TestBed.inject(JhiAlertService);
        const translationService = TestBed.inject(TranslateService);
        const errorDetailString = 'artemisApp.exam.validation.tooFewExerciseGroups';
        const httpError = new HttpErrorResponse({
            error: { errorKey: errorDetailString },
            status: 400,
        });
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');

        studentExams = [];
        const generateStudentExamsStub = sinon.stub(examManagementService, 'generateStudentExams').returns(throwError(httpError));

        studentExamsComponentFixture.detectChanges();

        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).to.equal(false);
        const alertServiceSpy = sinon.spy(alertService, 'error');
        const translationServiceSpy = sinon.spy(translationService, 'instant');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).to.exist;
        expect(generateStudentExamsButton.nativeElement.disabled).to.equal(false);
        generateStudentExamsButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;
        expect(translationServiceSpy).to.have.been.calledOnceWithExactly(errorDetailString);

        generateStudentExamsStub.restore();
    });

    it('should generate student exams after warning the user that the existing are deleted', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');

        studentExamsComponentFixture.detectChanges();
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });

        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamStarted).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;
        const generateStudentExamsSpy = sinon.spy(examManagementService, 'generateStudentExams');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).to.exist;
        expect(generateStudentExamsButton.nativeElement.disabled).to.equal(false);
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).to.equal(true);
        generateStudentExamsButton.nativeElement.click();
        expect(modalServiceOpenStub).to.have.been.called;
        expect(generateStudentExamsSpy).to.have.been.calledOnce;
        expect(studentExamsComponent.studentExams.length).to.equal(2);
        modalServiceOpenStub.restore();
    });

    it('should generate missing student exams', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');
        studentExams = [studentExamOne!];
        studentExamsComponentFixture.detectChanges();
        studentExams = [studentExamOne!, studentExamTwo!];

        expect(studentExamsComponent.hasStudentsWithoutExam).to.equal(true);
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamStarted).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(studentExamsComponent.studentExams.length).to.equal(1);
        expect(course).to.exist;
        const generateStudentExamsSpy = sinon.spy(examManagementService, 'generateMissingStudentExams');
        const generateMissingStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateMissingStudentExamsButton'));
        expect(generateMissingStudentExamsButton).to.exist;
        expect(generateMissingStudentExamsButton.nativeElement.disabled).to.equal(false);
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).to.equal(true);
        generateMissingStudentExamsButton.nativeElement.click();
        expect(generateStudentExamsSpy).to.have.been.calledOnce;
        expect(studentExamsComponent.studentExams.length).to.equal(2);
    });

    it('should correctly catch HTTPError when generating missing student exams', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const alertService = TestBed.inject(JhiAlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');

        const generateMissingStudentExamsStub = sinon.stub(examManagementService, 'generateMissingStudentExams').returns(throwError(httpError));
        studentExams = [studentExamOne!];
        studentExamsComponentFixture.detectChanges();

        const alertServiceSpy = sinon.spy(alertService, 'error');
        expect(studentExamsComponent.hasStudentsWithoutExam).to.equal(true);
        const generateMissingStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateMissingStudentExamsButton'));
        expect(generateMissingStudentExamsButton).to.exist;
        expect(generateMissingStudentExamsButton.nativeElement.disabled).to.equal(false);
        generateMissingStudentExamsButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        generateMissingStudentExamsStub.restore();
    });

    it('should start the exercises of students', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');
        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamStarted).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;

        const startExercisesSpy = sinon.spy(examManagementService, 'startExercises');
        const startExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#startExercisesButton'));
        expect(startExercisesButton).to.exist;
        expect(startExercisesButton.nativeElement.disabled).to.equal(false);

        startExercisesButton.nativeElement.click();
        expect(startExercisesSpy).to.have.been.calledOnce;
    });

    it('should correctly catch HTTPError when starting the exercises of the students', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const alertService = TestBed.inject(JhiAlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        course.isAtLeastInstructor = true;
        exam.startDate = moment().add(120, 'seconds');

        const startExercisesStub = sinon.stub(examManagementService, 'startExercises').returns(throwError(httpError));
        studentExamsComponentFixture.detectChanges();

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const startExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#startExercisesButton'));
        expect(startExercisesButton).to.exist;
        expect(startExercisesButton.nativeElement.disabled).to.equal(false);
        startExercisesButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        startExercisesStub.restore();
    });

    it('should unlock all repositories of the students', () => {
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });

        course.isAtLeastInstructor = true;

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;
        const unlockAllRepositories = sinon.spy(examManagementService, 'unlockAllRepositories');
        const unlockAllRepositoriesButton = studentExamsComponentFixture.debugElement.query(By.css('#handleUnlockAllRepositoriesButton'));
        expect(unlockAllRepositoriesButton).to.exist;
        expect(unlockAllRepositoriesButton.nativeElement.disabled).to.equal(false);
        unlockAllRepositoriesButton.nativeElement.click();
        expect(modalServiceOpenStub).to.have.been.called;
        expect(unlockAllRepositories).to.have.been.calledOnce;

        modalServiceOpenStub.restore();
    });

    it('should correctly catch HTTPError when unlocking all repositories', () => {
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });

        const alertService = TestBed.inject(JhiAlertService);
        course.isAtLeastInstructor = true;
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const unlockRepoStub = sinon.stub(examManagementService, 'unlockAllRepositories').returns(throwError(httpError));

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const unlockAllRepositoriesButton = studentExamsComponentFixture.debugElement.query(By.css('#handleUnlockAllRepositoriesButton'));
        expect(unlockAllRepositoriesButton).to.exist;
        expect(unlockAllRepositoriesButton.nativeElement.disabled).to.equal(false);
        unlockAllRepositoriesButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        modalServiceOpenStub.restore();
        unlockRepoStub.restore();
    });

    it('should lock all repositories of the students', () => {
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });

        course.isAtLeastInstructor = true;

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;
        const lockAllRepositories = sinon.spy(examManagementService, 'lockAllRepositories');
        const lockAllRepositoriesButton = studentExamsComponentFixture.debugElement.query(By.css('#lockAllRepositoriesButton'));
        expect(lockAllRepositoriesButton).to.exist;
        expect(lockAllRepositoriesButton.nativeElement.disabled).to.equal(false);
        lockAllRepositoriesButton.nativeElement.click();
        expect(modalServiceOpenStub).to.have.been.called;
        expect(lockAllRepositories).to.have.been.calledOnce;

        modalServiceOpenStub.restore();
    });

    it('should correctly catch HTTPError when locking all repositories', () => {
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = sinon.stub(modalService, 'open').returns(<NgbModalRef>{
            componentInstance,
            result,
        });

        const alertService = TestBed.inject(JhiAlertService);
        course.isAtLeastInstructor = true;
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const lockRepoStub = sinon.stub(examManagementService, 'lockAllRepositories').returns(throwError(httpError));

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const lockAllRepositoriesButton = studentExamsComponentFixture.debugElement.query(By.css('#lockAllRepositoriesButton'));
        expect(lockAllRepositoriesButton).to.exist;
        expect(lockAllRepositoriesButton.nativeElement.disabled).to.equal(false);
        lockAllRepositoriesButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        modalServiceOpenStub.restore();
        lockRepoStub.restore();
    });

    it('should evaluate Quiz exercises', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().subtract(200, 'seconds');
        exam.endDate = moment().subtract(100, 'seconds');

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamOver).to.equal(true);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;
        const evaluateQuizExercises = sinon.spy(examManagementService, 'evaluateQuizExercises');
        const evaluateQuizExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#evaluateQuizExercisesButton'));

        expect(evaluateQuizExercisesButton).to.exist;
        expect(evaluateQuizExercisesButton.nativeElement.disabled).to.equal(false);
        evaluateQuizExercisesButton.nativeElement.click();
        expect(evaluateQuizExercises).to.have.been.calledOnce;
    });

    it('should correctly catch HTTPError when evaluating quiz exercises', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = moment().subtract(200, 'seconds');
        exam.endDate = moment().subtract(100, 'seconds');
        const alertService = TestBed.inject(JhiAlertService);

        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponent.isLoading).to.equal(false);
        expect(studentExamsComponent.isExamOver).to.equal(true);
        expect(studentExamsComponent.course.isAtLeastInstructor).to.equal(true);
        expect(course).to.exist;

        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        const evaluateQuizExercisesStub = sinon.stub(examManagementService, 'evaluateQuizExercises').returns(throwError(httpError));
        studentExamsComponentFixture.detectChanges();

        const alertServiceSpy = sinon.spy(alertService, 'error');
        const evaluateQuizExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#evaluateQuizExercisesButton'));
        expect(evaluateQuizExercisesButton).to.exist;
        expect(evaluateQuizExercisesButton.nativeElement.disabled).to.equal(false);
        evaluateQuizExercisesButton.nativeElement.click();
        expect(alertServiceSpy).to.have.been.calledOnce;

        evaluateQuizExercisesStub.restore();
    });
});
