import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { Observable, of } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ComplaintsStudentViewComponent', () => {
    const complaintTimeLimitDays = 7;
    const course: Course = {
        id: 1,
        complaintsEnabled: true,
        maxComplaintTimeDays: complaintTimeLimitDays,
        requestMoreFeedbackEnabled: true,
        maxRequestMoreFeedbackTimeDays: complaintTimeLimitDays,
    };
    const courseWithoutFeedback: Course = { id: 1, complaintsEnabled: true, maxComplaintTimeDays: 7, requestMoreFeedbackEnabled: false };
    const examExercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
    const courseExercise: Exercise = {
        id: 1,
        teamMode: false,
        course,
        dueDate: dayjs().subtract(2, 'days'),
        assessmentDueDate: dayjs().subtract(1, 'day'),
        assessmentType: AssessmentType.MANUAL,
    } as Exercise;
    const result: Result = { id: 1, completionDate: dayjs().subtract(complaintTimeLimitDays - 1, 'day'), assessmentType: AssessmentType.MANUAL, rated: true } as Result;
    const submission: Submission = { results: [result] } as Submission;
    result.submission = submission;
    const resultWithoutCompletionDate: Result = { id: 1 } as Result;
    const user: User = { id: 1337 } as User;
    const participation: Participation = { id: 2, submissions: [submission], student: user } as Participation;
    submission.participation = participation;
    const defaultExam: Exam = {
        examStudentReviewStart: dayjs().subtract(complaintTimeLimitDays, 'day'),
        examStudentReviewEnd: dayjs().add(complaintTimeLimitDays, 'day'),
    } as Exam;
    const complaint = new Complaint();
    const numberOfComplaints = 42;

    let component: ComplaintsStudentViewComponent;
    let fixture: ComponentFixture<ComplaintsStudentViewComponent>;
    let complaintService: ComplaintService;
    let courseService: CourseManagementService;
    let accountService: AccountService;
    let serverDateService: ArtemisServerDateService;
    let numberOfAllowedComplaintsStub: jest.SpyInstance<Observable<number>, [courseId: number, teamMode?: boolean | undefined]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                ComplaintsStudentViewComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockComponent(ComplaintsFormComponent),
                MockComponent(ComplaintRequestComponent),
                MockComponent(ComplaintResponseComponent),
            ],
            providers: [
                {
                    provide: ComplaintService,
                    useClass: MockComplaintService,
                },
                {
                    provide: AccountService,
                    useClass: MockAccountService,
                },
                {
                    provide: CourseManagementService,
                    useClass: MockCourseManagementService,
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsStudentViewComponent);
                component = fixture.componentInstance;
                complaintService = TestBed.inject(ComplaintService);
                courseService = TestBed.inject(CourseManagementService);
                accountService = TestBed.inject(AccountService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                fixture.componentRef.setInput('participation', participation);
                fixture.componentRef.setInput('result', result);
                fixture.componentRef.setInput('exam', undefined);
                numberOfAllowedComplaintsStub = jest.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('Exam mode', () => {
        it('should initialize', fakeAsync(() => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            const complaintBySubmissionMock = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());
            const numberOfAllowedComplaintsMock = jest.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            tick(100);

            expectExamDefault();
            expect(component.complaint).toBeUndefined();
            expect(complaintBySubmissionMock).toHaveBeenCalledOnce();
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledOnce();
            expect(userMock).toHaveBeenCalledOnce();
        }));

        it('should initialize with complaint', fakeAsync(() => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);

            const complaintBySubmissionMock = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as EntityResponseType));
            const numberOfAllowedComplaintsMock = jest.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            tick(100);

            expectExamDefault();
            expect(component.complaint).toStrictEqual(complaint);
            expect(complaintBySubmissionMock).toHaveBeenCalledOnce();
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledOnce();
            expect(userMock).toHaveBeenCalledOnce();
        }));

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', fakeAsync(() => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            component.showSection = true;
            component.isCorrectUserToFileAction = true;
            const complaintBySubmissionMock = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.detectChanges();

            //Check if button is available
            expect(component.complaint).toBeUndefined();
            expect(complaintBySubmissionMock).toHaveBeenCalledOnce();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = jest.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            fixture.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.COMPLAINT);
            // Wait for setTimeout to execute
            tick();
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        }));

        it('should be visible on test run', fakeAsync(() => {
            const now = dayjs();
            const examWithFutureReview: Exam = { examStudentReviewStart: dayjs(now).add(1, 'day'), examStudentReviewEnd: dayjs(now).add(2, 'day') } as Exam;
            const serverDateStub = jest.spyOn(serverDateService, 'now').mockReturnValue(dayjs());
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', examWithFutureReview);
            fixture.componentRef.setInput('testRun', true);

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeTrue();
            expect(serverDateStub).not.toHaveBeenCalled();
        }));

        it('should be hidden if review start not set', fakeAsync(() => {
            const examWithoutReviewStart: Exam = { examStudentReviewEnd: dayjs() } as Exam;
            testVisibilityToBeHiddenWithExam(examWithoutReviewStart);
        }));

        it('should be hidden if review end not set', fakeAsync(() => {
            const examWithoutReviewEnd: Exam = { examStudentReviewStart: dayjs() } as Exam;
            testVisibilityToBeHiddenWithExam(examWithoutReviewEnd);
        }));

        function expectExamDefault() {
            expectDefault();
            expect(component.isExamMode).toBeTrue();
            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeTrue();
        }

        function testVisibilityToBeHiddenWithExam(exam: Exam) {
            jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', exam);

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeFalse();
        }
    });

    describe('Course mode', () => {
        it('should initialize', fakeAsync(() => {
            testInitWithResultStub(of());
            expect(component.complaint).toBeUndefined();
        }));

        it('should initialize with complaint', fakeAsync(() => {
            testInitWithResultStub(of({ body: complaint } as EntityResponseType));
            expect(component.complaint).toStrictEqual(complaint);
        }));

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', fakeAsync(() => {
            testInitWithResultStub(of());
            const courseWithMaxComplaints: Course = {
                ...course,
                maxComplaints: 3,
            };
            const exerciseWithMaxComplaints: Exercise = {
                ...courseExercise,
                course: courseWithMaxComplaints,
            };
            component.course = courseWithMaxComplaints;
            fixture.componentRef.setInput('exercise', exerciseWithMaxComplaints);

            component.showSection = true;
            component.isCorrectUserToFileAction = true;
            component.remainingNumberOfComplaints = 1;

            fixture.detectChanges();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = jest.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            fixture.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.COMPLAINT);
            tick(); // Wait for update to happen
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        }));

        it('should set complaint type MORE_FEEDBACK and scroll to complaint form when pressing complaint', fakeAsync(() => {
            testInitWithResultStub(of());
            component.showSection = true;
            component.isCorrectUserToFileAction = true;

            fixture.detectChanges();

            //Check if button is available
            expect(component.complaint).toBeUndefined();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = jest.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#more-feedback');
            button.click();

            fixture.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.MORE_FEEDBACK);
            tick(); // Wait for update to happen
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        }));

        it('should not be available if before or at assessment due date', fakeAsync(() => {
            const exercise: Exercise = { id: 1, teamMode: false, course, assessmentDueDate: dayjs() } as Exercise;
            const resultMatchingDate: Result = { id: 1, completionDate: dayjs(exercise.assessmentDueDate) } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultMatchingDate);

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeFalse();
        }));

        it('should not be available if assessment due date not set and completion date is out of period', fakeAsync(() => {
            const exercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'day') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeFalse();
        }));

        it('should not be available if completionDate after assessment due date and date is out of period', fakeAsync(() => {
            const exercise: Exercise = {
                id: 1,
                teamMode: false,
                course,
                assessmentDueDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day'),
            } as Exercise;
            const resultMatchingDate: Result = { ...result, completionDate: dayjs(exercise.assessmentDueDate!).add(1, 'day') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultMatchingDate);

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeFalse();
        }));

        it('should be available if result was before due date', fakeAsync(() => {
            const exercise: Exercise = { id: 1, teamMode: false, course, dueDate: dayjs().subtract(1, 'minute'), assessmentType: AssessmentType.MANUAL } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'days') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeTrue();
            expect(component.timeOfComplaintValid).toBeTrue();
        }));

        it('should be available if result was before assessment due date', fakeAsync(() => {
            const exercise: Exercise = {
                id: 1,
                teamMode: false,
                course,
                dueDate: dayjs().subtract(complaintTimeLimitDays + 1, 'days'),
                assessmentDueDate: dayjs().subtract(1, 'minute'),
                assessmentType: AssessmentType.MANUAL,
            } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'days') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeTrue();
            expect(component.timeOfComplaintValid).toBeTrue();
        }));

        it('complaints should be available if feedback requests disabled', fakeAsync(() => {
            fixture.componentRef.setInput('exercise', {
                ...courseExercise,
                course: courseWithoutFeedback,
                assessmentDueDate: dayjs().subtract(2),
            } as Exercise);
            component.course = courseWithoutFeedback;

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeTrue();
            expect(component.timeOfComplaintValid).toBeTrue();
            expect(component.timeOfFeedbackRequestValid).toBeFalse();
        }));

        it('feedback requests should be available if complaints are disabled', fakeAsync(() => {
            const courseWithoutComplaints = {
                ...course,
                complaintsEnabled: false,
                maxComplaintTimeDays: undefined,
                maxComplaints: undefined,
                maxTeamComplaints: undefined,
            } as Course;
            fixture.componentRef.setInput('exercise', {
                ...courseExercise,
                course: courseWithoutComplaints,
                assessmentDueDate: dayjs().subtract(2),
            } as Exercise);
            component.course = courseWithoutComplaints;

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeTrue();
            expect(component.timeOfComplaintValid).toBeFalse();
            expect(component.timeOfFeedbackRequestValid).toBeTrue();
        }));

        it('no action should be allowed if the result is automatic for a non automatic exercise', fakeAsync(() => {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('result', { ...result, assessmentType: AssessmentType.AUTOMATIC, rated: false });

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeTrue();
            expect(component.timeOfComplaintValid).toBeFalse();
            expect(component.timeOfFeedbackRequestValid).toBeFalse();
        }));

        function expectCourseDefault() {
            expectDefault();
            expect(component.isExamMode).toBeFalse();
            expect(component.timeOfFeedbackRequestValid).toBeTrue();
            expect(component.timeOfComplaintValid).toBeTrue();
        }

        function testInitWithResultStub(content: Observable<EntityResponseType>) {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('result', result);
            const complaintBySubmissionStub = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(content);
            const userStub = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            tick(100);

            expectCourseDefault();
            expect(complaintBySubmissionStub).toHaveBeenCalledOnce();
            expect(numberOfAllowedComplaintsStub).toHaveBeenCalledOnce();
            expect(userStub).toHaveBeenCalledOnce();
        }
    });

    function expectDefault() {
        expect(component.submission).toStrictEqual(submission);
        expect(component.course).toStrictEqual(course);
        expect(component.showSection).toBeTrue();
        expect(component.formComplaintType).toBeUndefined();
        expect(component.remainingNumberOfComplaints).toStrictEqual(numberOfComplaints);
        expect(component.isCorrectUserToFileAction).toBeTrue();
        expect(result.submission?.participation).toStrictEqual(participation);
    }

    it('should set time of complaint invalid without completion date', fakeAsync(() => {
        const participationWithoutCompletionDate: Participation = { id: 2, results: [resultWithoutCompletionDate], submissions: [submission], student: user } as Participation;
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('participation', participationWithoutCompletionDate);
        fixture.componentRef.setInput('result', resultWithoutCompletionDate);

        fixture.detectChanges();
        tick(100);

        expect(component.timeOfComplaintValid).toBeFalse();
    }));

    it('complaint should be possible with long assessment periods', fakeAsync(() => {
        fixture.componentRef.setInput('exercise', { ...courseExercise, assessmentDueDate: dayjs().subtract(3, 'day') });
        fixture.componentRef.setInput('result', { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day') });

        fixture.detectChanges();
        tick(100);

        expect(component.showSection).toBeTrue();
        expect(component.timeOfComplaintValid).toBeTrue();
        expect(component.timeOfFeedbackRequestValid).toBeTrue();
    }));
});
