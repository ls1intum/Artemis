import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockComponent, MockDirective, MockPipe, MockProvider, MockModule } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs';
import { By } from '@angular/platform-browser';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamAssessmentButtonsComponent } from 'app/course/dashboards/assessment-dashboard/exam-assessment-buttons/exam-assessment-buttons.component';

describe('ExamAssessmentButtons', () => {
    let examAssessmentButtonsFixture: ComponentFixture<ExamAssessmentButtonsComponent>;
    let examAssessmentButtonsComponent: ExamAssessmentButtonsComponent;
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
        MockProvider(AlertService),
        MockProvider(ArtemisTranslatePipe),
        MockDirective(TranslateDirective),
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
        { provide: TranslateService, useClass: MockTranslateService },
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
        exam.endDate = dayjs();
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
            imports: [RouterTestingModule.withRoutes([]), MockModule(NgxDatatableModule)],
            declarations: [
                ExamAssessmentButtonsComponent,
                MockComponent(StudentExamStatusComponent),
                MockComponent(AlertComponent),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(DataTableComponent),
            ],
            providers,
        })
            .compileComponents()
            .then(() => {
                examAssessmentButtonsFixture = TestBed.createComponent(ExamAssessmentButtonsComponent);
                examAssessmentButtonsComponent = examAssessmentButtonsFixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                examManagementService = TestBed.inject(ExamManagementService);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should automatically assess modeling and text exercises of unsubmitted student exams', () => {
        studentExamOne!.workingTime = 10;
        exam.startDate = dayjs().subtract(200, 'seconds');
        exam.endDate = dayjs().subtract(100, 'seconds');
        exam.gracePeriod = 0;
        course.isAtLeastInstructor = true;

        examAssessmentButtonsFixture.detectChanges();
        expect(examAssessmentButtonsComponent.isLoading).toEqual(false);
        expect(examAssessmentButtonsComponent.isExamOver).toEqual(true);
        expect(course).toBeTruthy();
        const assessSpy = jest.spyOn(examManagementService, 'assessUnsubmittedExamModelingAndTextParticipations');
        const assessButton = examAssessmentButtonsFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).toBeTruthy();
        assessButton.nativeElement.click();
        expect(assessSpy).toBeCalled();
    });

    it('should correctly catch HTTPError when assessing unsubmitted exams', () => {
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        studentExamOne!.workingTime = 10;
        exam.startDate = dayjs().subtract(200, 'seconds');
        exam.endDate = dayjs().subtract(100, 'seconds');
        exam.gracePeriod = 0;
        course.isAtLeastInstructor = true;

        examAssessmentButtonsFixture.detectChanges();
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        expect(examAssessmentButtonsComponent.isLoading).toEqual(false);
        expect(examAssessmentButtonsComponent.isExamOver).toEqual(true);
        expect(course).toBeTruthy();
        jest.spyOn(examManagementService, 'assessUnsubmittedExamModelingAndTextParticipations').mockReturnValue(throwError(httpError));
        const assessButton = examAssessmentButtonsFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).toBeTruthy();
        assessButton.nativeElement.click();
        expect(alertServiceSpy).toBeCalled();
    });

    it('should evaluate Quiz exercises', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().subtract(200, 'seconds');
        exam.endDate = dayjs().subtract(100, 'seconds');

        examAssessmentButtonsFixture.detectChanges();
        expect(examAssessmentButtonsComponent.isLoading).toEqual(false);
        expect(examAssessmentButtonsComponent.isExamOver).toEqual(true);
        expect(examAssessmentButtonsComponent.course.isAtLeastInstructor).toEqual(true);
        expect(course).toBeTruthy();
        const evaluateQuizExercises = jest.spyOn(examManagementService, 'evaluateQuizExercises');
        const evaluateQuizExercisesButton = examAssessmentButtonsFixture.debugElement.query(By.css('#evaluateQuizExercisesButton'));

        expect(evaluateQuizExercisesButton).toBeTruthy();
        expect(evaluateQuizExercisesButton.nativeElement.disabled).toEqual(false);

        evaluateQuizExercisesButton.nativeElement.click();
        expect(evaluateQuizExercises).toBeCalled();
    });

    it('should correctly catch HTTPError when evaluating quiz exercises', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().subtract(200, 'seconds');
        exam.endDate = dayjs().subtract(100, 'seconds');
        const alertService = TestBed.inject(AlertService);

        examAssessmentButtonsFixture.detectChanges();
        expect(examAssessmentButtonsComponent.isLoading).toEqual(false);
        expect(examAssessmentButtonsComponent.isExamOver).toEqual(true);
        expect(examAssessmentButtonsComponent.course.isAtLeastInstructor).toEqual(true);
        expect(course).toBeTruthy();

        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        jest.spyOn(examManagementService, 'evaluateQuizExercises').mockReturnValue(throwError(httpError));
        examAssessmentButtonsFixture.detectChanges();

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const evaluateQuizExercisesButton = examAssessmentButtonsFixture.debugElement.query(By.css('#evaluateQuizExercisesButton'));
        expect(evaluateQuizExercisesButton).toBeTruthy();
        expect(evaluateQuizExercisesButton.nativeElement.disabled).toEqual(false);
        evaluateQuizExercisesButton.nativeElement.click();
        expect(alertServiceSpy).toBeCalled();
    });

    it('should not show assess unsubmitted student exam modeling and text participations', () => {
        // user is not an instructor
        examAssessmentButtonsFixture.detectChanges();
        const assessButton = examAssessmentButtonsFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).toBeNull();
    });

    it('should disable show assess unsubmitted student exam modeling and text participations', () => {
        course.isAtLeastInstructor = true;

        // exam is not over
        examAssessmentButtonsFixture.detectChanges();
        const assessButton = examAssessmentButtonsFixture.debugElement.query(By.css('#assessUnsubmittedExamModelingAndTextParticipationsButton'));
        expect(assessButton).toBeTruthy();
        expect(assessButton.nativeElement.disabled).toEqual(true);
    });
});
