import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ListOfComplaintsComponent } from 'app/assessment/manage/list-of-complaints/list-of-complaints.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { User } from 'app/account/user/user.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SortService } from 'app/foundation/service/sort.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

describe('ListOfComplaintsComponent', () => {
    let fixture: ComponentFixture<ListOfComplaintsComponent>;
    let comp: ListOfComplaintsComponent;

    let complaintService: ComplaintService;
    let activatedRoute: MockActivatedRoute;
    let translateService: TranslateService;
    let router: Router;
    let datePipe: ArtemisDatePipe;

    let findAllStub: MockInstance<ComplaintService['findAll']>;
    let findAllForTutorStub: MockInstance<ComplaintService['findAllForTutor']>;

    const complaint1 = {
        id: 1,
        accepted: true,
    } as Complaint;
    const complaint2 = {
        id: 2,
        accepted: false,
    } as Complaint;
    const complaint3 = {
        id: 3,
    } as Complaint;
    const complaint4 = {
        id: 4,
        student: {},
    } as Complaint;
    const complaint5 = {
        id: 5,
        accepted: false,
        student: {},
    } as Complaint;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ListOfComplaintsComponent, TranslatePipeMock, MockComponent(FaIconComponent)],
            providers: [
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(SortService),
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
            ],
        })
            .compileComponents()
            .then(() => {
                vi.spyOn(TestBed.inject(AccountService), 'isAtLeastInstructorInCourseWithId').mockReturnValue(true);
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;

                complaintService = fixture.debugElement.injector.get(ComplaintService);
                translateService = fixture.debugElement.injector.get(TranslateService);
                activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
                router = fixture.debugElement.injector.get(Router);
                datePipe = fixture.debugElement.injector.get(ArtemisDatePipe);

                findAllStub = vi.spyOn(complaintService, 'findAll');
                findAllForTutorStub = vi.spyOn(complaintService, 'findAllForTutor');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('loadComplaints', () => {
        it('chooses explicit tutor exercise scope and ignores another tutor in the route', () => {
            vi.spyOn(TestBed.inject(AccountService), 'isAtLeastInstructorInCourseWithId').mockReturnValue(false);
            activatedRoute.setParameters({ tutorId: 12, courseId: 34, exerciseId: 56, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();
            expect(findAllForTutorStub).toHaveBeenCalledExactlyOnceWith({ exerciseId: 56, complaintType: ComplaintType.MORE_FEEDBACK });
            expect(findAllStub).not.toHaveBeenCalled();
        });

        it('chooses explicit tutor course scope without a tutor route parameter', () => {
            vi.spyOn(TestBed.inject(AccountService), 'isAtLeastInstructorInCourseWithId').mockReturnValue(false);
            activatedRoute.setParameters({ courseId: 34, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();
            expect(findAllForTutorStub).toHaveBeenCalledExactlyOnceWith({ courseId: 34, complaintType: ComplaintType.MORE_FEEDBACK });
            expect(findAllStub).not.toHaveBeenCalled();
        });

        it.each([
            ['for tutor by exercise', { tutorId: 12, courseId: 34, exerciseId: 56 }, { exerciseId: 56, complaintType: ComplaintType.MORE_FEEDBACK, tutorId: 12 }],
            ['for tutor by exam', { tutorId: 12, courseId: 34, examId: 56 }, { courseId: 34, complaintType: ComplaintType.MORE_FEEDBACK, tutorId: 12 }],
            ['for tutor by course', { tutorId: 12, courseId: 34 }, { courseId: 34, complaintType: ComplaintType.MORE_FEEDBACK, tutorId: 12 }],
            ['general by exercise', { courseId: 12, exerciseId: 34 }, { exerciseId: 34, complaintType: ComplaintType.MORE_FEEDBACK }],
            ['general by exam', { courseId: 12, examId: 34 }, { courseId: 12, examId: 34 }],
            ['general by course', { courseId: 12 }, { courseId: 12, complaintType: ComplaintType.MORE_FEEDBACK }],
        ])('finds %s', (_scope, routeParameters, expectedQuery) => {
            activatedRoute.setParameters({ ...routeParameters, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllStub).toHaveBeenCalledExactlyOnceWith(expectedQuery);
            expect(findAllForTutorStub).not.toHaveBeenCalled();
        });

        it('process complaints without student information', () => {
            findAllStub.mockReturnValue(of([complaint1, complaint2, complaint3]));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3]);
        });

        it('process complaints with student information', () => {
            findAllStub.mockReturnValue(of([complaint1, complaint2, complaint3, complaint4]));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3, complaint4]);

            findAllStub.mockReturnValue(of([complaint1, complaint2, complaint3, complaint5]));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3]);
        });
    });

    it('triggerAddressedComplaints', () => {
        const complaints = [complaint1, complaint2, complaint3, complaint4, complaint5];
        const freeComplaints = [complaint3, complaint4];
        findAllStub.mockReturnValue(of(complaints));
        comp.loadComplaints();
        expect(comp.showAddressedComplaints()).toBe(false);
        expect(comp.complaintsToShow()).toEqual(freeComplaints);

        comp.triggerAddressedComplaints();

        expect(comp.showAddressedComplaints()).toBe(true);
        expect(comp.complaintsToShow()).toEqual(complaints);

        comp.triggerAddressedComplaints();

        expect(comp.showAddressedComplaints()).toBe(false);
        expect(comp.complaintsToShow()).toEqual(freeComplaints);
    });

    describe('calculateComplaintLockStatus', () => {
        it('complaint unlocked', () => {
            const complaint = new Complaint();
            complaint.id = 42;
            complaint.result = new Result();
            complaint.complaintText = 'Test text';
            complaint.complaintType = ComplaintType.MORE_FEEDBACK;
            vi.spyOn(translateService, 'instant');

            comp.calculateComplaintLockStatus(complaint);

            expect(translateService.instant).toHaveBeenCalledTimes(1);
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.locks.notUnlocked');
        });

        it('complaint locked by the current user', () => {
            const endDate = dayjs().add(2, 'days');
            const expectedDate = datePipe.transform(endDate);
            const complaint = createComplaint(ComplaintType.MORE_FEEDBACK, endDate);
            complaint.id = 42;
            complaint.result = new Result();
            vi.spyOn(complaintService, 'isComplaintLockedByLoggedInUser').mockReturnValue(true);
            vi.spyOn(complaintService, 'isComplaintLocked').mockReturnValue(true);
            vi.spyOn(translateService, 'instant');

            comp.calculateComplaintLockStatus(complaint);

            expect(translateService.instant).toHaveBeenCalledTimes(1);
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.locks.lockInformationYou', { endDate: `${expectedDate}` });
        });

        it('complaint locked by another user', () => {
            const reviewLogin = 'review';
            const endDate = dayjs().add(2, 'days');
            const expectedDate = datePipe.transform(endDate);
            const complaint = new Complaint();
            complaint.id = 42;
            complaint.result = new Result();
            complaint.complaintText = 'Test text';
            complaint.complaintType = ComplaintType.MORE_FEEDBACK;
            complaint.complaintResponse = new ComplaintResponse();
            complaint.complaintResponse.isCurrentlyLocked = true;
            complaint.complaintResponse.reviewer = { login: reviewLogin } as User;
            complaint.complaintResponse.lockEndDate = endDate;
            vi.spyOn(complaintService, 'isComplaintLockedByLoggedInUser').mockReturnValue(false);
            vi.spyOn(complaintService, 'isComplaintLocked').mockReturnValue(true);
            vi.spyOn(translateService, 'instant');

            comp.calculateComplaintLockStatus(complaint);

            expect(translateService.instant).toHaveBeenCalledTimes(1);
            expect(translateService.instant).toHaveBeenCalledWith('artemisApp.locks.lockInformation', { endDate: `${expectedDate}`, user: reviewLogin });
        });
    });

    it('navigate for openAssessmentEditor', () => {
        testOpenAssessmentEditor(ComplaintType.MORE_FEEDBACK, false, 0);
    });

    it('uses correct correction round for accepted complaints', () => {
        testOpenAssessmentEditor(ComplaintType.COMPLAINT, true, 1);
    });

    it('uses correct correction round for rejected complaints', () => {
        testOpenAssessmentEditor(ComplaintType.COMPLAINT, false, 0);
    });

    it('uses correct correction round for accepted more feedback requests', () => {
        testOpenAssessmentEditor(ComplaintType.MORE_FEEDBACK, true, 0);
    });

    function testOpenAssessmentEditor(type: ComplaintType, accepted: boolean, expectedCorrectionRound: number) {
        const submissionId = 13;
        const participationId = 69;
        const exerciseId = 1337;
        const courseId = 77;
        const complaint = createComplaintWithSubmissionAndResult(submissionId, participationId, exerciseId, courseId, type);
        activatedRoute.data = of({ complaintType: type });
        complaint.accepted = accepted;
        vi.spyOn(router, 'navigate');
        activatedRoute.setParameters({ courseId });

        comp.ngOnInit();
        comp.openAssessmentEditor(complaint);

        expect(comp.correctionRound).toBe(expectedCorrectionRound);
        expect(router.navigate).toHaveBeenCalledTimes(1);
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', `${courseId}`, 'text-exercises', `${exerciseId}`, 'submissions', `${submissionId}`, 'assessment'], {
            queryParams: { 'correction-round': expectedCorrectionRound },
        });
    }

    function createComplaintWithSubmissionAndResult(submissionId: number, participationId: number, exerciseId: number, courseId: number, type?: ComplaintType): Complaint {
        const course = new Course();
        course.id = courseId;
        const complaint = createComplaint(type);
        complaint.id = 42;
        complaint.result = new Result();
        complaint.result.submission = new TextSubmission();
        complaint.result.submission.id = submissionId;
        complaint.result.submission.participation = new StudentParticipation();
        complaint.result.submission.participation.id = participationId;
        complaint.result.submission.participation.exercise = new TextExercise(course, undefined);
        complaint.result.submission.participation.exercise.id = exerciseId;
        return complaint;
    }

    function createComplaint(type = ComplaintType.MORE_FEEDBACK, endDate = dayjs().add(2, 'days')): Complaint {
        const userLogin = 'user';
        const complaint = new Complaint();
        complaint.complaintText = 'Test text';
        complaint.complaintType = type;
        complaint.complaintResponse = new ComplaintResponse();
        complaint.complaintResponse.isCurrentlyLocked = true;
        complaint.complaintResponse.reviewer = { login: userLogin } as User;
        complaint.complaintResponse.lockEndDate = endDate;
        return complaint;
    }

    it.each(['4', '5'])('should filter complaints accordingly', (filterOption: string) => {
        const addressedComplaints = [complaint1, complaint2, complaint5];
        const openComplaints = [complaint3, complaint4];

        const complaints = [complaint1, complaint2, complaint3, complaint4, complaint5];
        findAllStub.mockReturnValue(of(complaints));
        comp.filterOption.set(Number(filterOption));
        comp.loadComplaints();

        switch (Number(filterOption)) {
            case 4:
                // This filter option indicates that the user selected the part of the pie representing the number of addressed complaints
                // -> Only addressed complaints should be shown
                expect(comp.complaintsToShow()).toEqual(addressedComplaints);
                expect(comp.showAddressedComplaints()).toBe(true);
                break;
            case 5:
                // This filter option indicates that the user selected the part of the pie representing the number of open complaints
                // -> Only open complaints should be shown
                expect(comp.complaintsToShow()).toEqual(openComplaints);
                expect(comp.showAddressedComplaints()).toBe(false);
                break;
        }

        comp.resetFilterOptions();

        expect(comp.complaintsToShow()).toEqual(openComplaints);
        expect(comp.filterOption()).toBeUndefined();
    });

    it('assessorOptions and filter use privacy-safe assessorKey and assessorLabel for multiple foreign tutors', () => {
        const foreignTutorA = { id: 1, assessorKey: '101', assessorLabel: 'Tutor Two' } as Complaint;
        const foreignTutorB = { id: 2, assessorKey: '102', assessorLabel: 'Instructor One' } as Complaint;
        const foreignTutorAAgain = { id: 3, assessorKey: '101', assessorLabel: 'Tutor Two' } as Complaint;
        // accepted undefined so default addressed-filter keeps them visible
        comp.complaints.set([foreignTutorA, foreignTutorB, foreignTutorAAgain]);

        expect(comp.assessorOptions()).toEqual([
            { key: '102', label: 'Instructor One' },
            { key: '101', label: 'Tutor Two' },
        ]);

        comp.onAssessorFilterChange('101');

        expect(comp.complaintsToShow().map((complaint) => complaint.id)).toEqual([1, 3]);
    });

    it('clears loading when the mine request fails', () => {
        findAllStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });

        comp.ngOnInit();

        expect(comp.loading()).toBe(false);
    });

    it('requests the exercise-scoped all list on exercise-scoped routes', () => {
        activatedRoute.setParameters({ courseId: 12, exerciseId: 34, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        comp.setComplaintScope('all');

        expect(findAllForTutorStub).toHaveBeenCalledExactlyOnceWith({ exerciseId: 34, complaintType: ComplaintType.COMPLAINT, allComplaintsForTutor: true });
    });

    it('requests the course-scoped all list on course-scoped routes', () => {
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        comp.setComplaintScope('all');

        expect(findAllForTutorStub).toHaveBeenCalledExactlyOnceWith({ courseId: 12, complaintType: ComplaintType.COMPLAINT, allComplaintsForTutor: true });
    });

    it('sorts the assessor column by the same value the cell renders', () => {
        const sortService = fixture.debugElement.injector.get(SortService);
        const sortByFunctionSpy = vi.spyOn(sortService, 'sortByFunction');
        const ownComplaint = { id: 1, result: { assessor: { name: 'Zoe Tutor' } } } as Complaint;
        const foreignComplaint = { id: 2, assessorLabel: 'Alice Tutor' } as Complaint;
        comp.complaintsToShow.set([ownComplaint, foreignComplaint]);
        comp.complaintsSortingPredicate = 'assessorName';

        comp.sortRows();

        const sortKeyOf = sortByFunctionSpy.mock.calls[0][1] as (complaint: Complaint) => unknown;
        expect(sortKeyOf(ownComplaint)).toBe('Zoe Tutor');
        expect(sortKeyOf(foreignComplaint)).toBe('Alice Tutor');
    });

    it('applies the column header sort and re-sorts the complaints', () => {
        const sortService = fixture.debugElement.injector.get(SortService);
        const sortByPropertySpy = vi.spyOn(sortService, 'sortByProperty');
        const sortByFunctionSpy = vi.spyOn(sortService, 'sortByFunction');
        const first = { id: 1 } as Complaint;
        const second = { id: 2 } as Complaint;
        comp.complaintsToShow.set([first, second]);
        expect(comp.complaintsSortingPredicate).toBe('id');
        expect(comp.complaintsReverseOrder).toBe(false);

        comp['onSortChange']({ field: 'accepted', order: 1 });

        expect(comp.complaintsSortingPredicate).toBe('accepted');
        expect(comp.complaintsReverseOrder).toBe(true);
        expect(sortByPropertySpy).toHaveBeenCalledExactlyOnceWith([first, second], 'accepted', true);

        comp['onSortChange']({ field: 'lockStatus', order: -1 });

        expect(comp.complaintsSortingPredicate).toBe('lockStatus');
        expect(comp.complaintsReverseOrder).toBe(false);
        expect(sortByFunctionSpy).toHaveBeenCalledExactlyOnceWith([first, second], expect.any(Function), false);
    });

    it('uses the more-feedback translation root when the route complaint type is MORE_FEEDBACK', () => {
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.MORE_FEEDBACK });
        comp.ngOnInit();

        expect(comp.scopeTranslationKeyRoot()).toBe('artemisApp.moreFeedback.list');
    });

    it('resets mine/all caches when the route identity changes', () => {
        findAllStub.mockReturnValue(of([complaint3]));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();
        findAllForTutorStub.mockReturnValue(of([]));
        comp.setComplaintScope('all');
        expect(comp.allComplaintsForTutorLoaded()).toBe(true);

        findAllStub.mockReturnValue(of([complaint4]));
        activatedRoute.setParameters({ courseId: 12, exerciseId: 34, complaintType: ComplaintType.COMPLAINT });

        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.assessorFilter()).toBeUndefined();
        expect(comp.loading()).toBe(false);
        expect(comp.complaintsToShow()).toEqual([complaint4]);
    });

    it('switches between mine and all using the cached all list without a second request', () => {
        findAllStub.mockReturnValue(of([complaint3]));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        findAllForTutorStub.mockReturnValue(of([complaint3, complaint4]));
        comp.setComplaintScope('all');
        expect(findAllForTutorStub).toHaveBeenCalledTimes(1);
        expect(comp.allComplaintsForTutorLoaded()).toBe(true);
        expect(comp.complaintsToShow().map((complaint) => complaint.id)).toEqual([3, 4]);

        comp.setComplaintScope('mine');
        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.complaintsToShow()).toEqual([complaint3]);

        // Same scope is a no-op; a second All uses the cache.
        comp.setComplaintScope('mine');
        comp.setComplaintScope('all');
        expect(findAllForTutorStub).toHaveBeenCalledTimes(1);
        expect(comp.complaintsToShow().map((complaint) => complaint.id)).toEqual([3, 4]);
    });

    it('restores the mine list when the all request fails', () => {
        findAllStub.mockReturnValue(of([complaint3]));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        findAllForTutorStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp.setComplaintScope('all');

        expect(comp.isLoadingAllComplaints()).toBe(false);
        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.assessorFilter()).toBeUndefined();
        expect(comp.complaintsToShow()).toEqual([complaint3]);
    });
});
