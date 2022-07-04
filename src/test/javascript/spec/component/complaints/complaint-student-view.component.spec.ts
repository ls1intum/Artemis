import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { ArtemisTestModule } from '../../test.module';
import { Exercise } from 'app/entities/exercise.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exam } from 'app/entities/exam.model';
import { Submission } from 'app/entities/submission.model';
import { Complaint } from 'app/entities/complaint.model';
import { Observable, of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ComplaintsFormComponent } from 'app/complaints/form/complaints-form.component';
import { ComplaintRequestComponent } from 'app/complaints/request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/complaints/response/complaint-response.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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
    const courseExercise: Exercise = { id: 1, teamMode: false, course, assessmentDueDate: dayjs().subtract(1, 'day'), assessmentType: AssessmentType.MANUAL } as Exercise;
    const submission: Submission = {} as Submission;
    const result: Result = { id: 1, completionDate: dayjs().subtract(complaintTimeLimitDays - 1, 'day'), assessmentType: AssessmentType.MANUAL } as Result;
    const resultWithoutCompletionDate: Result = { id: 1 } as Result;
    const user: User = { id: 1337 } as User;
    const participation: Participation = { id: 2, results: [result], submissions: [submission], student: user } as Participation;
    const defaultExam: Exam = {
        examStudentReviewStart: dayjs().subtract(complaintTimeLimitDays, 'day'),
        examStudentReviewEnd: dayjs().add(complaintTimeLimitDays, 'day'),
    } as Exam;
    const complaint = new Complaint();
    const numberOfComplaints = 42;

    let component: ComplaintsStudentViewComponent;
    let fixture: ComponentFixture<ComplaintsStudentViewComponent>;
    let complaintService: ComplaintService;
    let accountService: AccountService;
    let serverDateService: ArtemisServerDateService;
    let numberOfAllowedComplaintsStub: jest.SpyInstance<Observable<number>, [courseId: number, teamMode?: boolean | undefined]>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ComplaintsStudentViewComponent);
                component = fixture.componentInstance;
                complaintService = TestBed.inject(ComplaintService);
                accountService = TestBed.inject(AccountService);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                component.participation = participation;
                component.result = result;
                numberOfAllowedComplaintsStub = jest.spyOn(complaintService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('Exam mode', () => {
        it('should initialize', fakeAsync(() => {
            component.exercise = examExercise;
            component.result = result;
            component.exam = defaultExam;
            const complaintBySubmissionMock = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());
            const numberOfAllowedComplaintsMock = jest.spyOn(complaintService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            tick(100);

            expectExamDefault();
            expect(component.complaint).toBe(undefined);
            expect(complaintBySubmissionMock).toHaveBeenCalledOnce();
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledOnce();
            expect(userMock).toHaveBeenCalledOnce();
        }));

        it('should initialize with complaint', fakeAsync(() => {
            component.exercise = examExercise;
            component.result = result;
            component.exam = defaultExam;
            const complaintBySubmissionMock = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as EntityResponseType));
            const numberOfAllowedComplaintsMock = jest.spyOn(complaintService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            tick(100);

            expectExamDefault();
            expect(component.complaint).toStrictEqual(complaint);
            expect(complaintBySubmissionMock).toHaveBeenCalledOnce();
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledOnce();
            expect(userMock).toHaveBeenCalledOnce();
        }));

        it('should be visible on test run', fakeAsync(() => {
            const now = dayjs();
            const examWithFutureReview: Exam = { examStudentReviewStart: dayjs(now).add(1, 'day'), examStudentReviewEnd: dayjs(now).add(2, 'day') } as Exam;
            const serverDateStub = jest.spyOn(serverDateService, 'now').mockReturnValue(dayjs());
            component.exercise = examExercise;
            component.result = result;
            component.exam = examWithFutureReview;
            component.testRun = true;

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

            component.exercise = examExercise;
            component.result = result;
            component.exam = exam;

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeFalse();
        }
    });

    describe('Course mode', () => {
        it('should initialize', fakeAsync(() => {
            testInitWithResultStub(of());
            expect(component.complaint).toBe(undefined);
        }));

        it('should initialize with complaint', fakeAsync(() => {
            testInitWithResultStub(of({ body: complaint } as EntityResponseType));
            expect(component.complaint).toStrictEqual(complaint);
        }));

        it('should not be available if before or at assessment due date', fakeAsync(() => {
            const exercise: Exercise = { id: 1, teamMode: false, course, assessmentDueDate: dayjs() } as Exercise;
            const resultMatchingDate: Result = { id: 1, completionDate: dayjs(exercise.assessmentDueDate) } as Result;
            component.exercise = exercise;
            component.result = resultMatchingDate;

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeFalse();
        }));

        it('should not be available if assessment due date not set and completion date is out of period', fakeAsync(() => {
            const exercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'day') } as Result;
            component.exercise = exercise;
            component.result = resultDateOutOfLimits;

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
            component.exercise = exercise;
            component.result = resultMatchingDate;

            fixture.detectChanges();
            tick(100);

            expect(component.timeOfFeedbackRequestValid).toBeFalse();
            expect(component.timeOfComplaintValid).toBeFalse();
        }));

        it('complaints should be available if feedback requests disabled', fakeAsync(() => {
            component.exercise = {
                ...courseExercise,
                course: courseWithoutFeedback,
                assessmentDueDate: dayjs().subtract(2),
            } as Exercise;
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
            component.exercise = {
                ...courseExercise,
                course: courseWithoutComplaints,
                assessmentDueDate: dayjs().subtract(2),
            } as Exercise;
            component.course = courseWithoutComplaints;

            fixture.detectChanges();
            tick(100);

            expect(component.showSection).toBeTrue();
            expect(component.timeOfComplaintValid).toBeFalse();
            expect(component.timeOfFeedbackRequestValid).toBeTrue();
        }));

        it('no action should be allowed if the result is automatic for a non automatic exercise', fakeAsync(() => {
            component.exercise = courseExercise;
            component.result = { ...result, assessmentType: AssessmentType.AUTOMATIC };

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
            component.exercise = courseExercise;
            component.result = result;
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
        expect(component.formComplaintType).toBe(undefined);
        expect(component.remainingNumberOfComplaints).toStrictEqual(numberOfComplaints);
        expect(component.isCorrectUserToFileAction).toBeTrue();
        expect(result.participation).toStrictEqual(participation);
    }

    it('should set time of complaint invalid without completion date', fakeAsync(() => {
        const participationWithoutCompletionDate: Participation = { id: 2, results: [resultWithoutCompletionDate], submissions: [submission], student: user } as Participation;
        component.exercise = courseExercise;
        component.participation = participationWithoutCompletionDate;
        component.result = resultWithoutCompletionDate;

        fixture.detectChanges();
        tick(100);

        expect(component.timeOfComplaintValid).toBeFalse();
    }));

    it('complaint should be possible with long assessment periods', fakeAsync(() => {
        component.exercise = { ...courseExercise, assessmentDueDate: dayjs().subtract(3, 'day') };
        component.result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day') };

        fixture.detectChanges();
        tick(100);

        expect(component.showSection).toBeTrue();
        expect(component.timeOfComplaintValid).toBeTrue();
        expect(component.timeOfFeedbackRequestValid).toBeTrue();
    }));
});
