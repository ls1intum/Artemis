import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, convertToParamMap, provideRouter } from '@angular/router';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { User } from 'app/core/user/user.model';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

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
    const referenceDateNow = dayjs();

    const providers = [
        provideRouter([]),
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
            getExerciseStartStatus: () => {
                return of(
                    new HttpResponse({
                        body: undefined,
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
        } as any as ExamManagementService),
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
        LocalStorageService,
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
        { provide: WebsocketService, useClass: MockWebsocketService },
        { provide: NgbModal, useClass: MockNgbModalService },
        MockProvider(ProfileService, { getProfileInfo: () => of({ activeProfiles: [] }) }, 'useValue'),
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
        exam.examUsers = [
            Object.assign({ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false }, studentOne, { user: studentOne }),
            Object.assign({ didCheckImage: false, didCheckLogin: false, didCheckName: false, didCheckRegistrationNumber: false }, studentTwo, { user: studentTwo }),
        ];
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
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        const courseManagementService = TestBed.inject(CourseManagementService);
        const studentExamService = TestBed.inject(StudentExamService);

        const findCourseSpy = jest.spyOn(courseManagementService, 'find');
        const findExamSpy = jest.spyOn(examManagementService, 'find');
        const findAllStudentExamsSpy = jest.spyOn(studentExamService, 'findAllForExam');

        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponentFixture).toBeDefined();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findExamSpy).toHaveBeenCalledOnce();
        expect(findAllStudentExamsSpy).toHaveBeenCalledOnce();
        expect(studentExamsComponent.course).toEqual(course);
        expect(studentExamsComponent.studentExams).toEqual(studentExams);
        expect(studentExamsComponent.exam).toEqual(exam);
        expect(studentExamsComponent.hasStudentsWithoutExam).toBeFalse();
        expect(studentExamsComponent.longestWorkingTime).toEqual(studentExamOne!.workingTime);
        expect(studentExamsComponent.isExamOver).toBeFalse();
        expect(studentExamsComponent.isLoading).toBeFalse();
    });

    it('should generate student exams if there are none', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');

        studentExams = [];
        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponent.isLoading).toBeFalse();
        expect(studentExamsComponent.isExamStarted).toBeFalse();
        expect(studentExamsComponent.course.isAtLeastInstructor).toBeTrue();
        expect(course).toBeTruthy();

        studentExams = [studentExamOne!, studentExamTwo!];

        const generateStudentExamsSpy = jest.spyOn(examManagementService, 'generateStudentExams');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).toBeTruthy();
        expect(generateStudentExamsButton.nativeElement.disabled).toBeFalse();
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).toBeFalse();
        generateStudentExamsButton.nativeElement.click();
        expect(generateStudentExamsSpy).toHaveBeenCalledOnce();
        expect(studentExamsComponent.studentExams).toHaveLength(2);
    });

    it('should correctly catch HTTPError and get additional error when generating student exams', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const artemisTranslationPipe = TestBed.inject(ArtemisTranslatePipe);
        const alertService = TestBed.inject(AlertService);
        const errorDetailString = 'artemisApp.studentExams.missingStudentExamGenerationError';
        const httpError = new HttpErrorResponse({
            error: { errorKey: errorDetailString },
            status: 400,
        });
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');

        studentExams = [];
        jest.spyOn(examManagementService, 'generateStudentExams').mockReturnValue(throwError(() => httpError));

        studentExamsComponentFixture.detectChanges();

        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).toBeFalse();
        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const translationSpy = jest.spyOn(artemisTranslationPipe, 'transform');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).toBeTruthy();
        expect(generateStudentExamsButton.nativeElement.disabled).toBeFalse();
        generateStudentExamsButton.nativeElement.click();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(translationSpy).toHaveBeenCalledWith(errorDetailString);
    });

    it('should generate student exams after warning the user that the existing are deleted', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');

        studentExamsComponentFixture.detectChanges();
        const componentInstance = { title: String, text: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });

        expect(studentExamsComponent.isLoading).toBeFalse();
        expect(studentExamsComponent.isExamStarted).toBeFalse();
        expect(studentExamsComponent.course.isAtLeastInstructor).toBeTrue();
        expect(course).toBeTruthy();
        const generateStudentExamsSpy = jest.spyOn(examManagementService, 'generateStudentExams');
        const generateStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateStudentExamsButton'));
        expect(generateStudentExamsButton).toBeTruthy();
        expect(generateStudentExamsButton.nativeElement.disabled).toBeFalse();
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).toBeTrue();
        generateStudentExamsButton.nativeElement.click();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
        expect(generateStudentExamsSpy).toHaveBeenCalledOnce();
        expect(studentExamsComponent.studentExams).toHaveLength(2);
    });

    it('should generate missing student exams', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');
        studentExams = [studentExamOne!];
        studentExamsComponentFixture.detectChanges();
        studentExams = [studentExamOne!, studentExamTwo!];

        expect(studentExamsComponent.hasStudentsWithoutExam).toBeTrue();
        expect(studentExamsComponent.isLoading).toBeFalse();
        expect(studentExamsComponent.isExamStarted).toBeFalse();
        expect(studentExamsComponent.course.isAtLeastInstructor).toBeTrue();
        expect(studentExamsComponent.studentExams).toHaveLength(1);
        expect(course).toBeTruthy();
        const generateStudentExamsSpy = jest.spyOn(examManagementService, 'generateMissingStudentExams');
        const generateMissingStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateMissingStudentExamsButton'));
        expect(generateMissingStudentExamsButton).toBeTruthy();
        expect(generateMissingStudentExamsButton.nativeElement.disabled).toBeFalse();
        expect(!!studentExamsComponent.studentExams && !!studentExamsComponent.studentExams.length).toBeTrue();
        generateMissingStudentExamsButton.nativeElement.click();
        expect(generateStudentExamsSpy).toHaveBeenCalledOnce();
        expect(studentExamsComponent.studentExams).toHaveLength(2);
    });

    it('should correctly catch HTTPError when generating missing student exams', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');

        jest.spyOn(examManagementService, 'generateMissingStudentExams').mockReturnValue(throwError(() => httpError));
        studentExams = [studentExamOne!];
        studentExamsComponentFixture.detectChanges();

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        expect(studentExamsComponent.hasStudentsWithoutExam).toBeTrue();
        const generateMissingStudentExamsButton = studentExamsComponentFixture.debugElement.query(By.css('#generateMissingStudentExamsButton'));
        expect(generateMissingStudentExamsButton).toBeTruthy();
        expect(generateMissingStudentExamsButton.nativeElement.disabled).toBeFalse();
        generateMissingStudentExamsButton.nativeElement.click();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should start the exercises of students', () => {
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');
        studentExamsComponentFixture.detectChanges();

        expect(studentExamsComponent.isLoading).toBeFalse();
        expect(studentExamsComponent.isExamStarted).toBeFalse();
        expect(studentExamsComponent.course.isAtLeastInstructor).toBeTrue();
        expect(course).toBeTruthy();

        const startExercisesSpy = jest.spyOn(examManagementService, 'startExercises');
        const startExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#startExercisesButton'));
        expect(startExercisesButton).toBeTruthy();
        expect(startExercisesButton.nativeElement.disabled).toBeFalse();

        startExercisesButton.nativeElement.click();
        expect(startExercisesSpy).toHaveBeenCalledOnce();
    });

    it('should correctly catch HTTPError when starting the exercises of the students', () => {
        examManagementService = TestBed.inject(ExamManagementService);
        const alertService = TestBed.inject(AlertService);
        const httpError = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
        course.isAtLeastInstructor = true;
        exam.startDate = dayjs().add(120, 'seconds');

        jest.spyOn(examManagementService, 'startExercises').mockReturnValue(throwError(() => httpError));
        studentExamsComponentFixture.detectChanges();

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        const startExercisesButton = studentExamsComponentFixture.debugElement.query(By.css('#startExercisesButton'));
        expect(startExercisesButton).toBeTruthy();
        expect(startExercisesButton.nativeElement.disabled).toBeFalse();
        startExercisesButton.nativeElement.click();
        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it.each([
        { status: undefined, expected: { running: false, percentage: 0, eta: undefined } },
        { status: { finished: 0, failed: 0, overall: 0, startedAt: referenceDateNow }, expected: { running: false, percentage: 100, eta: undefined } },
        { status: { finished: 0, failed: 0, overall: 1000, startedAt: referenceDateNow }, expected: { running: true, percentage: 0, eta: undefined } },
        { status: { finished: 10, failed: 0, overall: 1000, startedAt: referenceDateNow.subtract(100, 's') }, expected: { running: true, percentage: 1, eta: '2h45m0s' } },
        { status: { finished: 90, failed: 10, overall: 1000, startedAt: referenceDateNow.subtract(100, 's') }, expected: { running: true, percentage: 10, eta: '15m0s' } },
        { status: { finished: 990, failed: 10, overall: 1000, startedAt: referenceDateNow.subtract(100, 's') }, expected: { running: false, percentage: 100, eta: undefined } },
    ])('should correctly calculate exam preparation progress', ({ status, expected }) => {
        jest.useFakeTimers().setSystemTime(referenceDateNow.toDate());
        studentExamsComponent.setExercisePreparationStatus(status);
        expect(studentExamsComponent.exercisePreparationRunning).toBe(expected.running);
        expect(studentExamsComponent.exercisePreparationPercentage).toBe(expected.percentage);
        expect(studentExamsComponent.exercisePreparationEta).toBe(expected.eta);
        jest.useRealTimers();
    });
});
