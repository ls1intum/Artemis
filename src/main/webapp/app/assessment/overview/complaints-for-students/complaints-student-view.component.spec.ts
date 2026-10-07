import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ComplaintService, EntityResponseType } from 'app/assessment/shared/services/complaint.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Observable, of } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import dayjs from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

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
    const complaint = new ComplaintDTO();
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
            expect(component.complaint()).toBeUndefined();
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
            expect(component.complaint()).toStrictEqual(complaintService.convertComplaintFromServer(complaint, component.result()!));
            expect(complaintBySubmissionMock).toHaveBeenCalledTimes(1);
            expect(numberOfAllowedComplaintsMock).toHaveBeenCalledTimes(1);
            expect(userMock).toHaveBeenCalledTimes(1);
        });

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', async () => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            component.showSection.set(true);
            component.isCorrectUserToFileAction.set(true);
            const complaintBySubmissionMock = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.changeDetectorRef.detectChanges();

            //Check if button is available
            expect(component.complaint()).toBeUndefined();
            expect(complaintBySubmissionMock).toHaveBeenCalledTimes(1);

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('[data-testid="complaint-scrollpoint"]').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            await fixture.whenStable();

            expect(component.formComplaintType()).toBe(ComplaintType.COMPLAINT);
            // Wait for setTimeout to execute

            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
        });

        it('should place the scroll anchor after the complaint form, so that scrolling it into view reveals the end of the form', async () => {
            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', defaultExam);
            component.showSection.set(true);
            component.isCorrectUserToFileAction.set(true);
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());
            fixture.changeDetectorRef.detectChanges();
            // jsdom does not implement scrollIntoView
            fixture.nativeElement.querySelector('[data-testid="complaint-scrollpoint"]').scrollIntoView = vi.fn();

            fixture.debugElement.nativeElement.querySelector('#complain').click();
            await fixture.whenStable();
            fixture.changeDetectorRef.detectChanges();

            const form: HTMLElement = fixture.nativeElement.querySelector('jhi-complaint-form');
            const anchor: HTMLElement = fixture.nativeElement.querySelector('[data-testid="complaint-scrollpoint"]');
            expect(form).not.toBeNull();
            expect(anchor).not.toBeNull();
            expect(form.compareDocumentPosition(anchor) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
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

            expect(component.showSection()).toBe(true);
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
            expect(component.isExamMode()).toBe(true);
            expect(component.timeOfFeedbackRequestValid()).toBe(false);
            expect(component.timeOfComplaintValid()).toBe(true);
        }

        function testVisibilityToBeHiddenWithExam(exam: Exam) {
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());

            fixture.componentRef.setInput('exercise', examExercise);
            fixture.componentRef.setInput('result', result);
            fixture.componentRef.setInput('exam', exam);

            fixture.detectChanges();

            expect(component.showSection()).toBe(false);
        }
    });

    describe('Course mode', () => {
        it('should initialize', async () => {
            await testInitWithResultStub(of());
            expect(component.complaint()).toBeUndefined();
        });

        it('should initialize with complaint', async () => {
            await testInitWithResultStub(of({ body: complaint } as EntityResponseType));
            expect(component.complaint()).toStrictEqual(complaintService.convertComplaintFromServer(complaint, component.result()!));
        });

        it('should set complaint type COMPLAINT and scroll to complaint form when pressing complaint', async () => {
            await testInitWithResultStub(of());
            const courseWithMaxComplaints: Course = {
                ...course,
                maxComplaints: 3,
            };
            const exerciseWithMaxComplaints: Exercise = {
                ...courseExercise,
                course: courseWithMaxComplaints,
            };
            component.course.set(courseWithMaxComplaints);
            fixture.componentRef.setInput('exercise', exerciseWithMaxComplaints);

            component.showSection.set(true);
            component.isCorrectUserToFileAction.set(true);
            component.remainingNumberOfComplaints.set(1);

            fixture.changeDetectorRef.detectChanges();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('[data-testid="complaint-scrollpoint"]').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#complain');
            button.click();

            await fixture.whenStable();

            expect(component.formComplaintType()).toBe(ComplaintType.COMPLAINT);
            // setTimeout executes synchronously with mocked timers removed
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
        });

        it('should set complaint type MORE_FEEDBACK and scroll to complaint form when pressing complaint', async () => {
            await testInitWithResultStub(of());
            component.showSection.set(true);
            component.isCorrectUserToFileAction.set(true);

            fixture.changeDetectorRef.detectChanges();

            //Check if button is available
            expect(component.complaint()).toBeUndefined();

            // Mock complaint scrollpoint
            const scrollIntoViewMock = vi.fn();
            fixture.nativeElement.querySelector('[data-testid="complaint-scrollpoint"]').scrollIntoView = scrollIntoViewMock;

            const button = fixture.debugElement.nativeElement.querySelector('#more-feedback');
            button.click();

            await fixture.whenStable();

            expect(component.formComplaintType()).toBe(ComplaintType.MORE_FEEDBACK);
            // setTimeout executes synchronously with mocked timers removed
            expect(scrollIntoViewMock).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
        });

        it('should not be available if before or at assessment due date', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course, assessmentDueDate: dayjs() } as Exercise;
            const resultMatchingDate: Result = { id: 1, completionDate: dayjs(exercise.assessmentDueDate) } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultMatchingDate);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid()).toBe(false);
            expect(component.timeOfComplaintValid()).toBe(false);
        });

        it('should not be available if assessment due date not set and completion date is out of period', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'day') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid()).toBe(false);
            expect(component.timeOfComplaintValid()).toBe(false);
        });

        it('should not be available if completionDate after assessment due date and date is out of period', () => {
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

            expect(component.timeOfFeedbackRequestValid()).toBe(false);
            expect(component.timeOfComplaintValid()).toBe(false);
        });

        it('should be available if result was before due date', () => {
            const exercise: Exercise = { id: 1, teamMode: false, course, dueDate: dayjs().subtract(1, 'minute'), assessmentType: AssessmentType.MANUAL } as Exercise;
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 1, 'days') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(true);
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
            const resultDateOutOfLimits: Result = { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'days') } as Result;
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('result', resultDateOutOfLimits);

            fixture.detectChanges();

            expect(component.timeOfFeedbackRequestValid()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(true);
        });

        it('complaints should be available if feedback requests disabled', () => {
            fixture.componentRef.setInput('exercise', {
                ...courseExercise,
                course: courseWithoutFeedback,
                assessmentDueDate: dayjs().subtract(2),
            } as Exercise);
            component.course.set(courseWithoutFeedback);

            fixture.detectChanges();

            expect(component.showSection()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(true);
            expect(component.timeOfFeedbackRequestValid()).toBe(false);
        });

        it('feedback requests should be available if complaints are disabled', () => {
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
            component.course.set(courseWithoutComplaints);

            fixture.detectChanges();

            expect(component.showSection()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(false);
            expect(component.timeOfFeedbackRequestValid()).toBe(true);
        });

        it('no action should be allowed if the result is automatic for a non automatic exercise', () => {
            fixture.componentRef.setInput('exercise', courseExercise);
            fixture.componentRef.setInput('result', { ...result, assessmentType: AssessmentType.AUTOMATIC, rated: false });

            fixture.detectChanges();

            expect(component.showSection()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(false);
            expect(component.timeOfFeedbackRequestValid()).toBe(false);
        });

        function expectCourseDefault() {
            expectDefault();
            expect(component.isExamMode()).toBe(false);
            expect(component.timeOfFeedbackRequestValid()).toBe(true);
            expect(component.timeOfComplaintValid()).toBe(true);
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

    describe('layout', () => {
        async function renderCourseView(complaintResponse: Observable<EntityResponseType>) {
            const exerciseWithComplaints: Exercise = { ...courseExercise, course: { ...course, maxComplaints: 3 } };
            fixture.componentRef.setInput('exercise', exerciseWithComplaints);
            fixture.componentRef.setInput('result', result);
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(complaintResponse);
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));

            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        }

        it('should render the host as a block so that margins and the first line of the area are not split off', async () => {
            await renderCourseView(of());

            expect(fixture.nativeElement.classList.contains('block')).toBe(true);
        });

        it('should mark the host as the complaint area for the layout contract of the exam', async () => {
            await renderCourseView(of());

            expect(fixture.nativeElement.getAttribute('data-testid')).toBe('complaint-student-view');
        });

        it('should render the actions as small TUM AET UI buttons in one wrapping row', async () => {
            await renderCourseView(of());

            const actions = fixture.nativeElement.querySelector('.complaints-student-view__actions') as HTMLElement;
            expect(actions).not.toBeNull();
            expect(['flex', 'flex-wrap', 'gap-2'].every((utility) => actions.classList.contains(utility))).toBe(true);

            for (const id of ['complain', 'more-feedback']) {
                const button = actions.querySelector(`#${id}`) as HTMLButtonElement;
                expect(button).not.toBeNull();
                expect(button.classList.contains('btn')).toBe(false);
                expect(button.classList.contains('tumaet-ui-btn')).toBe(true);
                // The small size (text-sm, py-1.5) makes the button 34px high, like the page-level buttons.
                expect(button.classList.contains('tumaet:text-sm')).toBe(true);
                expect(button.classList.contains('tumaet:py-1.5')).toBe(true);
                expect(button.classList.contains('tumaet:py-2')).toBe(false);
            }
        });

        it('should keep the disabled state and the not-allowed class on the complain button', async () => {
            await renderCourseView(of());
            // The complaint period is over, so complaining is not allowed any more.
            component.timeOfComplaintValid.set(false);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('#complain') as HTMLButtonElement;
            expect(button.disabled).toBe(true);
            expect(button.classList.contains('not-allowed')).toBe(true);
        });

        it('should place the request and the response side by side only from 1200px and stack them below', async () => {
            await renderCourseView(of({ body: complaint } as EntityResponseType));

            const request = fixture.nativeElement.querySelector('jhi-complaint-request') as HTMLElement;
            const response = fixture.nativeElement.querySelector('jhi-complaint-response') as HTMLElement;
            const cards = fixture.nativeElement.querySelector('[data-testid="complaint-cards"]') as HTMLElement;
            expect(request).not.toBeNull();
            expect(response).not.toBeNull();

            // The viewport breakpoint of md would squeeze both columns next to the exam sidebar, so the columns start at 1200px, the xl breakpoint of Bootstrap.
            expect(['grid', 'grid-cols-1', 'min-[1200px]:grid-cols-2'].every((utility) => cards.classList.contains(utility))).toBe(true);
            expect(cards.classList.contains('min-[768px]:grid-cols-2')).toBe(false);
            // Both cards are cells of the same grid. Their two rows (header, text) are shared, so the texts line up even if a header wraps.
            expect(request.parentElement).toBe(cards);
            expect(response.parentElement).toBe(cards);
            expect(cards.classList.contains('grid-rows-[auto_1fr]')).toBe(true);
            expect(cards.classList.contains('row')).toBe(false);
        });

        it('should give the form half of the width from 1200px on and the full width below', async () => {
            await renderCourseView(of());
            component.formComplaintType.set(ComplaintType.COMPLAINT);
            fixture.detectChanges();

            const form = fixture.nativeElement.querySelector('jhi-complaint-form') as HTMLElement;
            expect(form).not.toBeNull();
            expect(form.classList.contains('min-[1200px]:w-1/2')).toBe(true);
            expect(form.classList.contains('min-[768px]:w-1/2')).toBe(false);
            expect(form.closest('.row')).toBeNull();
        });
    });

    describe('scroll target', () => {
        let scrolledElements: Element[];

        beforeEach(() => {
            scrolledElements = [];
            // jsdom does not implement scrollIntoView
            Object.defineProperty(Element.prototype, 'scrollIntoView', {
                configurable: true,
                writable: true,
                value: vi.fn(function (this: Element) {
                    scrolledElements.push(this);
                }),
            });
        });

        afterEach(() => {
            Reflect.deleteProperty(Element.prototype, 'scrollIntoView');
        });

        async function renderViews(count: number): Promise<ComponentFixture<ComplaintsStudentViewComponent>[]> {
            const exerciseWithComplaints: Exercise = { ...courseExercise, course: { ...course, maxComplaints: 3 } };
            vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of());
            const views = [fixture];
            for (let index = 1; index < count; index++) {
                views.push(TestBed.createComponent(ComplaintsStudentViewComponent));
            }
            // A zoneless fixture detects changes on its own, so the inputs are set before anything is awaited.
            for (const view of views) {
                view.componentRef.setInput('exercise', exerciseWithComplaints);
                view.componentRef.setInput('participation', participation);
                view.componentRef.setInput('result', result);
                view.componentRef.setInput('isCurrentUserSubmissionAuthor', true);
            }
            // Creating a fixture removes the root element of the previous ones from the document, but the exam summary shows all views at once.
            for (const view of [...views].reverse()) {
                document.body.prepend(view.nativeElement);
            }
            for (const view of views) {
                view.detectChanges();
                await view.whenStable();
                view.detectChanges();
            }
            return views;
        }

        it('should scroll to the form of the view whose button was clicked, not to the first view on the page', async () => {
            const [first, second] = await renderViews(2);
            expect(document.querySelectorAll('#complain')).toHaveLength(2);

            (second.nativeElement.querySelector('#complain') as HTMLButtonElement).click();
            await second.whenStable();

            expect(scrolledElements).toHaveLength(1);
            expect(second.nativeElement.contains(scrolledElements[0])).toBe(true);
            expect(first.nativeElement.contains(scrolledElements[0])).toBe(false);
            expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
        });

        it('should scroll to the form of the first view when its own button was clicked', async () => {
            const [first, second] = await renderViews(2);

            (first.nativeElement.querySelector('#complain') as HTMLButtonElement).click();
            await first.whenStable();

            expect(scrolledElements).toHaveLength(1);
            expect(first.nativeElement.contains(scrolledElements[0])).toBe(true);
            expect(second.nativeElement.contains(scrolledElements[0])).toBe(false);
        });

        it('should scroll to the form of the view whose more feedback button was clicked', async () => {
            const [first, second] = await renderViews(2);

            (second.nativeElement.querySelector('#more-feedback') as HTMLButtonElement).click();
            await second.whenStable();

            expect(scrolledElements).toHaveLength(1);
            expect(second.nativeElement.contains(scrolledElements[0])).toBe(true);
            expect(first.nativeElement.contains(scrolledElements[0])).toBe(false);
        });

        it('should only reveal the form instead of aligning it to the bottom edge, so a form that fits does not move the page', async () => {
            const [view] = await renderViews(1);

            (view.nativeElement.querySelector('#complain') as HTMLButtonElement).click();
            await view.whenStable();

            expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith(expect.objectContaining({ block: 'nearest' }));
            expect(Element.prototype.scrollIntoView).not.toHaveBeenCalledWith(expect.objectContaining({ block: 'end' }));
        });
    });

    function expectDefault() {
        expect(component.submission).toStrictEqual(submission);
        expect(component.course()).toStrictEqual(course);
        expect(component.showSection()).toBe(true);
        expect(component.formComplaintType()).toBeUndefined();
        expect(component.remainingNumberOfComplaints()).toStrictEqual(numberOfComplaints);
        expect(component.isCorrectUserToFileAction()).toBe(true);
        expect(result.submission?.participation).toStrictEqual(participation);
    }

    it('should set time of complaint invalid without completion date', () => {
        const participationWithoutCompletionDate: Participation = { id: 2, results: [resultWithoutCompletionDate], submissions: [submission], student: user } as Participation;
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('participation', participationWithoutCompletionDate);
        fixture.componentRef.setInput('result', resultWithoutCompletionDate);

        fixture.detectChanges();

        expect(component.timeOfComplaintValid()).toBe(false);
    });

    it('should hide the section for a practice participation, which is not graded and cannot be complained about', () => {
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('participation', { ...participation, testRun: true } as Participation);
        fixture.componentRef.setInput('result', result);

        fixture.detectChanges();

        expect(component.showSection()).toBe(false);
    });

    it('should hide the section for preliminary Athena feedback, which is a suggestion rather than an assessment', () => {
        fixture.componentRef.setInput('exercise', courseExercise);
        fixture.componentRef.setInput('result', { ...result, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result);

        fixture.detectChanges();

        expect(component.showSection()).toBe(false);
    });

    it('complaint should be possible with long assessment periods', () => {
        fixture.componentRef.setInput('exercise', { ...courseExercise, assessmentDueDate: dayjs().subtract(3, 'day') });
        fixture.componentRef.setInput('result', { ...result, completionDate: dayjs().subtract(complaintTimeLimitDays + 2, 'day') });

        fixture.detectChanges();

        expect(component.showSection()).toBe(true);
        expect(component.timeOfComplaintValid()).toBe(true);
        expect(component.timeOfFeedbackRequestValid()).toBe(true);
    });
});
