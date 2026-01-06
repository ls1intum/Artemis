import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { CourseComplaintConfiguration } from 'app/core/course/shared/entities/course-complaint-configuration.model';

describe('ComplaintsStudentViewComponent', () => {
    setupTestBed({ zoneless: true });
    const complaintTimeLimitDays = 7;
    const createComplaintConfiguration = (maxComplaintTimeDays: number, maxRequestMoreFeedbackTimeDays: number, maxComplaints?: number): CourseComplaintConfiguration => {
        const configuration = new CourseComplaintConfiguration();
        configuration.maxComplaintTimeDays = maxComplaintTimeDays;
        configuration.maxRequestMoreFeedbackTimeDays = maxRequestMoreFeedbackTimeDays;
        if (maxComplaints !== undefined) {
            configuration.maxComplaints = maxComplaints;
        }
        return configuration;
    };
    const cloneExercise = (
        base: Exercise,
        overrides?: {
            course?: Course;
            dueDate?: dayjs.Dayjs;
            assessmentDueDate?: dayjs.Dayjs;
            assessmentType?: AssessmentType;
        },
    ): Exercise => {
        const exercise: Exercise = {
            id: base.id,
            teamMode: base.teamMode,
            course: base.course,
            dueDate: base.dueDate,
            assessmentDueDate: base.assessmentDueDate,
            assessmentType: base.assessmentType,
        } as Exercise;
        if (!overrides) {
            return exercise;
        }
        if (overrides.course !== undefined) {
            exercise.course = overrides.course;
        }
        if (overrides.dueDate !== undefined) {
            exercise.dueDate = overrides.dueDate;
        }
        if (overrides.assessmentDueDate !== undefined) {
            exercise.assessmentDueDate = overrides.assessmentDueDate;
        }
        if (overrides.assessmentType !== undefined) {
            exercise.assessmentType = overrides.assessmentType;
        }
        return exercise;
    };
    const cloneResult = (
        base: Result,
        overrides?: {
            completionDate?: dayjs.Dayjs;
            assessmentType?: AssessmentType;
            rated?: boolean;
        },
    ): Result => {
        const result = new Result();
        result.id = base.id;
        result.completionDate = base.completionDate;
        result.assessmentType = base.assessmentType;
        result.rated = base.rated;
        result.submission = base.submission;
        if (!overrides) {
            return result;
        }
        if (overrides.completionDate !== undefined) {
            result.completionDate = overrides.completionDate;
        }
        if (overrides.assessmentType !== undefined) {
            result.assessmentType = overrides.assessmentType;
        }
        if (overrides.rated !== undefined) {
            result.rated = overrides.rated;
        }
        return result;
    };
    const complaintConfiguration = createComplaintConfiguration(complaintTimeLimitDays, complaintTimeLimitDays);
    const course = new Course();
    course.id = 1;
    course.complaintConfiguration = complaintConfiguration;
    const courseWithoutFeedback = new Course();
    courseWithoutFeedback.id = 1;
    courseWithoutFeedback.complaintConfiguration = createComplaintConfiguration(7, 0);
    const examExercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
    const courseExercise: Exercise = {
        id: 1,
        teamMode: false,
        course,
        dueDate: dayjs().subtract(2, 'days'),
        assessmentDueDate: dayjs().subtract(1, 'day'),
        assessmentType: AssessmentType.MANUAL,
    } as Exercise;
    const result = new Result();
    result.id = 1;
    result.completionDate = dayjs().subtract(complaintTimeLimitDays - 1, 'day');
    result.assessmentType = AssessmentType.MANUAL;
    result.rated = true;
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
    let numberOfAllowedComplaintsStub: MockInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                ComplaintsStudentViewComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockComponent(FaIconComponent),
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
                {
                    provide: ArtemisServerDateService,
                    useValue: { now: () => dayjs() },
                },
                MockProvider(TranslateService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(ComplaintsStudentViewComponent, {
                remove: { imports: [TranslateDirective, FaIconComponent, ComplaintsFormComponent, ComplaintRequestComponent, ComplaintResponseComponent] },
                add: {
                    imports: [
                        MockDirective(TranslateDirective),
                        MockComponent(FaIconComponent),
                        MockComponent(ComplaintsFormComponent),
                        MockComponent(ComplaintRequestComponent),
                        MockComponent(ComplaintResponseComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ComplaintsStudentViewComponent);
        component = fixture.componentInstance;
        complaintService = TestBed.inject(ComplaintService);
        courseService = TestBed.inject(CourseManagementService);
        accountService = TestBed.inject(AccountService);
        serverDateService = TestBed.inject(ArtemisServerDateService);
        fixture.componentRef.setInput('participation', participation);
        fixture.componentRef.setInput('result', result);
        fixture.componentRef.setInput('exam', undefined);
        numberOfAllowedComplaintsStub = vi.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Exam mode', () => {
        it('should initialize', async () => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            const complaintBySubmissionMock = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());
            const numberOfAllowedComplaintsMock = vi.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            await fixture.whenStable();

            expectExamDefault();
            expect(component.complaint).toBeUndefined();
            expect(complaintBySubmissionMock).toHaveBeenCalledTimes(1);
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledTimes(1);
            expect(userMock).toHaveBeenCalledTimes(1);
        });

        it('should initialize with complaint', async () => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);

            const complaintBySubmissionMock = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as EntityResponseType));
            const numberOfAllowedComplaintsMock = vi.spyOn(courseService, 'getNumberOfAllowedComplaintsInCourse').mockReturnValue(of(numberOfComplaints));
            const userMock = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            await fixture.whenStable();

            expectExamDefault();
            expect(component.complaint).toStrictEqual(complaint);
            expect(complaintBySubmissionMock).toHaveBeenCalledTimes(1);
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledTimes(1);
            expect(userMock).toHaveBeenCalledTimes(1);
        });

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', () => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            component.showSection = true;
            component.isCorrectUserToFileAction = true;
            const complaintBySubmissionMock = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.changeDetectorRef.detectChanges();

            //Check if button is available
            expect(component.complaint).toBeUndefined();
            expect(complaintBySubmissionMock).toHaveBeenCalledTimes(1);

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            fixture.changeDetectorRef.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.COMPLAINT);
            // Wait for setTimeout to execute

            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        });

        it('should be visible on test run', () => {
            const now = dayjs();
            const examWithFutureReview: Exam = { examStudentReviewStart: dayjs(now).add(1, 'day'), examStudentReviewEnd: dayjs(now).add(2, 'day') } as Exam;
            const serverDateStub = vi.spyOn(serverDateService, 'now').mockReturnValue(dayjs());
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', examWithFutureReview);
            fixture.componentRef.setInput('testRun', true);

            fixture.detectChanges();

            expect(component.showSection).toBe(true);
            expect(serverDateStub).not.toHaveBeenCalled();
        });

        it('should be hidden if review start not set', () => {
            const examWithoutReviewStart: Exam = { examStudentReviewEnd: dayjs() } as Exam;
            testVisibilityToBeHiddenWithExam(examWithoutReviewStart);
        });

        it('should be hidden if review end not set', () => {
            const examWithoutReviewEnd: Exam = { examStudentReviewStart: dayjs() } as Exam;
            testVisibilityToBeHiddenWithExam(examWithoutReviewEnd);
        });

        function expectExamDefault() {
            expectDefault();
            expect(component.isExamMode).toBe(true);
            expect(component.timeOfFeedbackRequestValid).toBe(false);
            expect(component.timeOfComplaintValid).toBe(true);
        }

        function testVisibilityToBeHiddenWithExam(exam: Exam) {
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', exam);

            fixture.detectChanges();

            expect(component.showSection).toBe(false);
        }
    });

    describe('Course mode', () => {
        it('should initialize', async () => {
            await testInitWithResultStub(of());
            expect(component.complaint).toBeUndefined();
        });

        it('should initialize with complaint', async () => {
            await testInitWithResultStub(of({ body: complaint } as EntityResponseType));
            expect(component.complaint).toStrictEqual(complaint);
        });

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', async () => {
            await testInitWithResultStub(of());
            const courseWithMaxComplaints = new Course();
            courseWithMaxComplaints.id = course.id;
            courseWithMaxComplaints.complaintConfiguration = createComplaintConfiguration(
                course.complaintConfiguration?.maxComplaintTimeDays ?? complaintTimeLimitDays,
                course.complaintConfiguration?.maxRequestMoreFeedbackTimeDays ?? complaintTimeLimitDays,
                3,
            );
            const exerciseWithMaxComplaints = cloneExercise(courseExercise, { course: courseWithMaxComplaints });
            component.course = courseWithMaxComplaints;
            fixture.componentRef.setInput('exercise', exerciseWithMaxComplaints);

            component.showSection = true;
            component.isCorrectUserToFileAction = true;
            component.remainingNumberOfComplaints = 1;

            fixture.changeDetectorRef.detectChanges();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            fixture.changeDetectorRef.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.COMPLAINT);
            // setTimeout executes synchronously with mocked timers removed
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        });

        it('should set complaint type MORE_FEEDBACK and scroll to complaint form when pressing complaint', async () => {
            await testInitWithResultStub(of());
            component.showSection = true;
            component.isCorrectUserToFileAction = true;

            fixture.changeDetectorRef.detectChanges();

            //Check if button is available
            expect(component.complaint).toBeUndefined();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('#complaintScrollpoint').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#more-feedback');
            button.click();

            fixture.changeDetectorRef.detectChanges();

            expect(component.formComplaintType).toBe(ComplaintType.MORE_FEEDBACK);
            // setTimeout executes synchronously with mocked timers removed
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'end' });
        });

        it('should not be available if before or at assessment due date', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course, assessmentDueDate: dayjs() } as Exercise;
            const resultMatchingDate: Result = { id: 1, completionDate: dayjs(exercise.assessmentDueDate) } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultMatchingDate);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid).toBe(false);
            expect(component.timeOfComplaintValid).toBe(false);
        });

        it('should not be available if assessment due date not set and completion date is out of period', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
            const resultDateOutOfLimits = cloneResult(result, { completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'day') });
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid).toBe(false);
            expect(component.timeOfComplaintValid).toBe(false);
        });

        it('should not be available if completionDate after assessment due date and date is out of period', () => {
            const exercise: Exercise = {
                id: 1,
                teamMode: false,
                course,
                assessmentDueDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day'),
            } as Exercise;
            const resultMatchingDate = cloneResult(result, { completionDate: dayjs(exercise.assessmentDueDate!).add(1, 'day') });
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultMatchingDate);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid).toBe(false);
            expect(component.timeOfComplaintValid).toBe(false);
        });

        it('should be available if result was before due date', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course, dueDate: dayjs().subtract(1, 'minute'), assessmentType: AssessmentType.MANUAL } as Exercise;
            const resultDateOutOfLimits = cloneResult(result, { completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'days') });
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid).toBe(true);
            expect(component.timeOfComplaintValid).toBe(true);
        });

        it('should be available if result was before assessment due date', () => {
            const exercise: Exercise = {
                id: 1,
                teamMode: false,
                course,
                dueDate: dayjs().subtract(complaintTimeLimitDays + 1, 'days'),
                assessmentDueDate: dayjs().subtract(1, 'minute'),
                assessmentType: AssessmentType.MANUAL,
            } as Exercise;
            const resultDateOutOfLimits = cloneResult(result, { completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'days') });
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid).toBe(true);
            expect(component.timeOfComplaintValid).toBe(true);
        });

        it('complaints should be available if feedback requests disabled', () => {
            fixture.componentRef.setInput('exercise', cloneExercise(courseExercise, { course: courseWithoutFeedback, assessmentDueDate: dayjs().subtract(2) }));
            component.course = courseWithoutFeedback;

            fixture.detectChanges();

            expect(component.showSection).toBe(true);
            expect(component.timeOfComplaintValid).toBe(true);
            expect(component.timeOfFeedbackRequestValid).toBe(false);
        });

        it('feedback requests should be available if complaints are disabled', () => {
            const courseWithoutComplaints = new Course();
            courseWithoutComplaints.id = course.id;
            courseWithoutComplaints.complaintConfiguration = createComplaintConfiguration(0, 7);
            fixture.componentRef.setInput('exercise', cloneExercise(courseExercise, { course: courseWithoutComplaints, assessmentDueDate: dayjs().subtract(2) }));
            component.course = courseWithoutComplaints;

            fixture.detectChanges();

            expect(component.showSection).toBe(true);
            expect(component.timeOfComplaintValid).toBe(false);
            expect(component.timeOfFeedbackRequestValid).toBe(true);
        });

        it('no action should be allowed if the result is automatic for a non automatic exercise', () => {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('result', cloneResult(result, { assessmentType: AssessmentType.AUTOMATIC, rated: false }));

            fixture.detectChanges();

            expect(component.showSection).toBe(true);
            expect(component.timeOfComplaintValid).toBe(false);
            expect(component.timeOfFeedbackRequestValid).toBe(false);
        });

        function expectCourseDefault() {
            expectDefault();
            expect(component.isExamMode).toBe(false);
            expect(component.timeOfFeedbackRequestValid).toBe(true);
            expect(component.timeOfComplaintValid).toBe(true);
        }

        async function testInitWithResultStub(content: Observable<EntityResponseType>) {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('result', result);
            const complaintBySubmissionStub = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(content);
            const userStub = vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            await fixture.whenStable();

            expectCourseDefault();
            expect(complaintBySubmissionStub).toHaveBeenCalledTimes(1);
            expect(numberOfAllowedComplaintsStub).toHaveBeenCalledTimes(1);
            expect(userStub).toHaveBeenCalledTimes(1);
        }
    });

    function expectDefault() {
        expect(component.submission).toStrictEqual(submission);
        expect(component.course).toStrictEqual(course);
        expect(component.showSection).toBe(true);
        expect(component.formComplaintType).toBeUndefined();
        expect(component.remainingNumberOfComplaints).toStrictEqual(numberOfComplaints);
        expect(component.isCorrectUserToFileAction).toBe(true);
        expect(result.submission?.participation).toStrictEqual(participation);
    }

    it('should set time of complaint invalid without completion date', () => {
        const participationWithoutCompletionDate: Participation = { id: 2, results: [resultWithoutCompletionDate], submissions: [submission], student: user } as Participation;
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('participation', participationWithoutCompletionDate);
        fixture.componentRef.setInput('result', resultWithoutCompletionDate);

        fixture.detectChanges();

        expect(component.timeOfComplaintValid).toBe(false);
    });

    it('complaint should be possible with long assessment periods', () => {
        fixture.componentRef.setInput('exercise', cloneExercise(courseExercise, { assessmentDueDate: dayjs().subtract(3, 'day') }));
        fixture.componentRef.setInput('result', cloneResult(result, { completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day') }));

        fixture.detectChanges();

        expect(component.showSection).toBe(true);
        expect(component.timeOfComplaintValid).toBe(true);
        expect(component.timeOfFeedbackRequestValid).toBe(true);
    });
});
