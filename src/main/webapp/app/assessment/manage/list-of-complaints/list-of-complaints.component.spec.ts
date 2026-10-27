import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintService, EntityResponseTypeArray, IComplaintService } from 'app/assessment/shared/services/complaint.service';
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
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({ template: '' })
class AssessmentStubComponent {}

describe('ListOfComplaintsComponent', () => {
    let fixture: ComponentFixture<ListOfComplaintsComponent>;
    let comp: ListOfComplaintsComponent;

    let complaintService: IComplaintService;
    let activatedRoute: MockActivatedRoute;
    let translateService: TranslateService;
    let router: Router;
    let datePipe: ArtemisDatePipe;

    let findAllByTutorIdForExerciseIdStub: MockInstance;
    let findAllByTutorIdForCourseIdStub: MockInstance;
    let findAllByExerciseIdStub: MockInstance;
    let findAllByCourseIdAndExamIdStub: MockInstance;
    let findAllByCourseIdStub: MockInstance;

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
                MockProvider(SortService),
                { provide: ComplaintService, useClass: MockComplaintService },
                provideRouter([{ path: 'course-management/:courseId/:exerciseType/:exerciseId/submissions/:submissionId/assessment', component: AssessmentStubComponent }]),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;

                complaintService = fixture.debugElement.injector.get(ComplaintService);
                translateService = fixture.debugElement.injector.get(TranslateService);
                activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
                router = fixture.debugElement.injector.get(Router);
                datePipe = fixture.debugElement.injector.get(ArtemisDatePipe);

                findAllByTutorIdForExerciseIdStub = vi.spyOn(complaintService, 'findAllByTutorIdForExerciseId');
                findAllByTutorIdForCourseIdStub = vi.spyOn(complaintService, 'findAllByTutorIdForCourseId');
                findAllByExerciseIdStub = vi.spyOn(complaintService, 'findAllByExerciseId');
                findAllByCourseIdAndExamIdStub = vi.spyOn(complaintService, 'findAllByCourseIdAndExamId');
                findAllByCourseIdStub = vi.spyOn(complaintService, 'findAllByCourseId');
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('loadComplaints', () => {
        it('find for tutor by exercise', () => {
            activatedRoute.setParameters({ tutorId: 12, courseId: 34, exerciseId: 56, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByTutorIdForExerciseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForExerciseIdStub).toHaveBeenCalledWith(12, 56, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find for tutor by exam', () => {
            activatedRoute.setParameters({ tutorId: 12, courseId: 34, examId: 56, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledWith(12, 34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find for tutor by course', () => {
            activatedRoute.setParameters({ tutorId: 12, courseId: 34, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledWith(12, 34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find general by exercise', () => {
            activatedRoute.setParameters({ courseId: 12, exerciseId: 34, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByExerciseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByExerciseIdStub).toHaveBeenCalledWith(34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByTutorIdForCourseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find general by exam', () => {
            activatedRoute.setParameters({ courseId: 12, examId: 34, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByCourseIdAndExamIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByCourseIdAndExamIdStub).toHaveBeenCalledWith(12, 34);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdStub);
        });

        it('find general by course', () => {
            activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.MORE_FEEDBACK });
            comp.ngOnInit();

            expect(findAllByCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByCourseIdStub).toHaveBeenCalledWith(12, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub);
        });

        it('process complaints without student information', () => {
            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3] } as EntityResponseTypeArray));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3]);
        });

        it('process complaints with student information', () => {
            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint4] } as EntityResponseTypeArray));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3, complaint4]);

            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint5] } as EntityResponseTypeArray));
            comp.loadComplaints();

            expect(comp.complaintsToShow()).toEqual([complaint3]);
        });
    });

    it('triggerAddressedComplaints', () => {
        const complaints = [complaint1, complaint2, complaint3, complaint4, complaint5];
        const freeComplaints = [complaint3, complaint4];
        findAllByCourseIdStub.mockReturnValue(of({ body: complaints } as EntityResponseTypeArray));
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

    describe('show complaint link', () => {
        const courseId = 77;
        const exerciseId = 1337;
        const assessmentPath = `/course-management/${courseId}/text-exercises/${exerciseId}/submissions/13/assessment`;

        beforeEach(() => {
            // The mock service has no row helpers; rows render the response time and its highlight.
            Object.assign(comp.complaintService, { getResponseTimeInSeconds: vi.fn().mockReturnValue(0), shouldHighlightComplaint: vi.fn().mockReturnValue(false) });
        });

        function showLinks(type: ComplaintType, complaints: Complaint[], correctionRound?: number): HTMLAnchorElement[] {
            activatedRoute.setParameters({ courseId, complaintType: type, ...(correctionRound !== undefined && { correctionRound }) });
            fixture.detectChanges();
            comp.complaintsToShow.set(complaints);
            fixture.detectChanges();
            return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="show-complaint"]'));
        }

        function complaintWithAssessment(type: ComplaintType, accepted: boolean | undefined, id = 42): Complaint {
            const complaint = createComplaintWithSubmissionAndResult(13, 69, exerciseId, courseId, type);
            complaint.id = id;
            complaint.accepted = accepted;
            return complaint;
        }

        it('links a rejected complaint to its assessment in the current correction round, so it can be opened in a new tab', () => {
            const [link] = showLinks(ComplaintType.COMPLAINT, [complaintWithAssessment(ComplaintType.COMPLAINT, false)]);

            expect(link.tagName).toBe('A');
            expect(link.getAttribute('href')).toBe(`${assessmentPath}?correction-round=0`);
        });

        it('links an accepted complaint to the next correction round', () => {
            const [link] = showLinks(ComplaintType.COMPLAINT, [complaintWithAssessment(ComplaintType.COMPLAINT, true)]);

            expect(link.getAttribute('href')).toBe(`${assessmentPath}?correction-round=1`);
        });

        it('keeps the correction round for an accepted more feedback request and labels it as such', () => {
            const [link] = showLinks(ComplaintType.MORE_FEEDBACK, [complaintWithAssessment(ComplaintType.MORE_FEEDBACK, true)]);

            expect(link.getAttribute('href')).toBe(`${assessmentPath}?correction-round=0`);
            expect(link.querySelector('[jhiTranslate="artemisApp.exerciseAssessmentDashboard.showMoreFeedbackRequests"]')).not.toBeNull();
            expect(link.querySelector('[jhiTranslate="artemisApp.exerciseAssessmentDashboard.showComplaint"]')).toBeNull();
        });

        it('does not add up the correction round across accepted complaints', () => {
            const links = showLinks(
                ComplaintType.COMPLAINT,
                [complaintWithAssessment(ComplaintType.COMPLAINT, true, 1), complaintWithAssessment(ComplaintType.COMPLAINT, true, 2)],
                1,
            );

            expect(links.map((link) => link.getAttribute('href'))).toEqual([`${assessmentPath}?correction-round=2`, `${assessmentPath}?correction-round=2`]);
        });

        it('shows no link for a complaint whose assessment cannot be opened', () => {
            const withoutSubmission = complaintWithAssessment(ComplaintType.COMPLAINT, false, 1);
            withoutSubmission.result!.submission = undefined;

            const links = showLinks(ComplaintType.COMPLAINT, [withoutSubmission, complaintWithAssessment(ComplaintType.COMPLAINT, false, 2)]);

            const rows: HTMLTableRowElement[] = Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
            expect(rows).toHaveLength(2);
            expect(rows[0].querySelector('[data-testid="show-complaint"]')).toBeNull();
            expect(links).toHaveLength(1);
            expect(rows[1].contains(links[0])).toBe(true);
        });

        it('navigates within the application on a plain click', async () => {
            const [link] = showLinks(ComplaintType.COMPLAINT, [complaintWithAssessment(ComplaintType.COMPLAINT, true)]);

            link.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
            await fixture.whenStable();

            expect(router.url).toBe(`${assessmentPath}?correction-round=1`);
        });
    });

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
        findAllByCourseIdStub.mockReturnValue(of({ body: complaints } as EntityResponseTypeArray));
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
        findAllByCourseIdStub.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });

        comp.ngOnInit();

        expect(comp.loading()).toBe(false);
    });

    it('requests the exercise-scoped all list on exercise-scoped routes', () => {
        const findAllForExerciseStub = vi.spyOn(complaintService, 'findAllWithoutStudentInformationForExerciseId').mockReturnValue(of(emptyComplaintResponse()));
        const findAllForCourseStub = vi.spyOn(complaintService, 'findAllWithoutStudentInformationForCourseId');
        activatedRoute.setParameters({ courseId: 12, exerciseId: 34, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        comp.setComplaintScope('all');

        expect(findAllForExerciseStub).toHaveBeenCalledExactlyOnceWith(34, ComplaintType.COMPLAINT);
        verifyNotCalled(findAllForCourseStub);
    });

    it('requests the course-scoped all list on course-scoped routes', () => {
        const findAllForCourseStub = vi.spyOn(complaintService, 'findAllWithoutStudentInformationForCourseId').mockReturnValue(of(emptyComplaintResponse()));
        const findAllForExerciseStub = vi.spyOn(complaintService, 'findAllWithoutStudentInformationForExerciseId');
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        comp.setComplaintScope('all');

        expect(findAllForCourseStub).toHaveBeenCalledExactlyOnceWith(12, ComplaintType.COMPLAINT);
        verifyNotCalled(findAllForExerciseStub);
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

    it('uses the more-feedback translation root when the route complaint type is MORE_FEEDBACK', () => {
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.MORE_FEEDBACK });
        comp.ngOnInit();

        expect(comp.scopeTranslationKeyRoot()).toBe('artemisApp.moreFeedback.list');
    });

    it('resets mine/all caches when the route identity changes', () => {
        findAllByCourseIdStub.mockReturnValue(of({ body: [complaint3] } as EntityResponseTypeArray));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();
        vi.spyOn(complaintService, 'findAllWithoutStudentInformationForCourseId').mockReturnValue(of(emptyComplaintResponse()));
        comp.setComplaintScope('all');
        expect(comp.allComplaintsForTutorLoaded()).toBe(true);

        findAllByExerciseIdStub.mockReturnValue(of({ body: [complaint4] } as EntityResponseTypeArray));
        activatedRoute.setParameters({ courseId: 12, exerciseId: 34, complaintType: ComplaintType.COMPLAINT });

        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.assessorFilter()).toBeUndefined();
        expect(comp.loading()).toBe(false);
        expect(comp.complaintsToShow()).toEqual([complaint4]);
    });

    it('switches between mine and all using the cached all list without a second request', () => {
        findAllByCourseIdStub.mockReturnValue(of({ body: [complaint3] } as EntityResponseTypeArray));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        const findAllForCourseStub = vi
            .spyOn(complaintService, 'findAllWithoutStudentInformationForCourseId')
            .mockReturnValue(of({ body: [complaint3, complaint4] } as EntityResponseTypeArray));
        comp.setComplaintScope('all');
        expect(findAllForCourseStub).toHaveBeenCalledTimes(1);
        expect(comp.allComplaintsForTutorLoaded()).toBe(true);
        expect(comp.complaintsToShow().map((complaint) => complaint.id)).toEqual([3, 4]);

        comp.setComplaintScope('mine');
        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.complaintsToShow()).toEqual([complaint3]);

        // Same scope is a no-op; a second All uses the cache.
        comp.setComplaintScope('mine');
        comp.setComplaintScope('all');
        expect(findAllForCourseStub).toHaveBeenCalledTimes(1);
        expect(comp.complaintsToShow().map((complaint) => complaint.id)).toEqual([3, 4]);
    });

    it('restores the mine list when the all request fails', () => {
        findAllByCourseIdStub.mockReturnValue(of({ body: [complaint3] } as EntityResponseTypeArray));
        activatedRoute.setParameters({ courseId: 12, complaintType: ComplaintType.COMPLAINT });
        comp.ngOnInit();

        vi.spyOn(complaintService, 'findAllWithoutStudentInformationForCourseId').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        comp.setComplaintScope('all');

        expect(comp.isLoadingAllComplaints()).toBe(false);
        expect(comp.allComplaintsForTutorLoaded()).toBe(false);
        expect(comp.assessorFilter()).toBeUndefined();
        expect(comp.complaintsToShow()).toEqual([complaint3]);
    });

    function emptyComplaintResponse(): EntityResponseTypeArray {
        return new HttpResponse<ComplaintDTO[]>({ body: [] });
    }

    function verifyNotCalled(...instances: MockInstance[]) {
        for (const spyInstance of instances) {
            expect(spyInstance).not.toHaveBeenCalled();
        }
    }
});
