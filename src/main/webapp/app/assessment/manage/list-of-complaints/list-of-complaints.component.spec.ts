import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { ComplaintService, EntityResponseTypeArray, IComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ListOfComplaintsComponent } from 'app/assessment/manage/list-of-complaints/list-of-complaints.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SortService } from 'app/shared/service/sort.service';
import dayjs from 'dayjs/esm';
import { MockComponent, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

describe('ListOfComplaintsComponent', () => {
    setupTestBed({ zoneless: true });
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
                { provide: Router, useClass: MockRouter },
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

            expect(comp.complaintsToShow).toEqual([complaint3]);
        });

        it('process complaints with student information', () => {
            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint4] } as EntityResponseTypeArray));
            comp.loadComplaints();

            expect(comp.complaintsToShow).toEqual([complaint3, complaint4]);

            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint5] } as EntityResponseTypeArray));
            comp.loadComplaints();

            expect(comp.complaintsToShow).toEqual([complaint3]);
        });
    });

    it('triggerAddressedComplaints', () => {
        const complaints = [complaint1, complaint2, complaint3, complaint4, complaint5];
        const freeComplaints = [complaint3, complaint4];
        findAllByCourseIdStub.mockReturnValue(of({ body: complaints } as EntityResponseTypeArray));
        comp.loadComplaints();
        expect(comp.showAddressedComplaints).toBe(false);
        expect(comp.complaintsToShow).toEqual(freeComplaints);

        comp.triggerAddressedComplaints();

        expect(comp.showAddressedComplaints).toBe(true);
        expect(comp.complaintsToShow).toEqual(complaints);

        comp.triggerAddressedComplaints();

        expect(comp.showAddressedComplaints).toBe(false);
        expect(comp.complaintsToShow).toEqual(freeComplaints);
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
        findAllByCourseIdStub.mockReturnValue(of({ body: complaints } as EntityResponseTypeArray));
        comp.filterOption = Number(filterOption);
        comp.loadComplaints();

        switch (Number(filterOption)) {
            case 4:
                // This filter option indicates that the user selected the part of the pie representing the number of addressed complaints
                // -> Only addressed complaints should be shown
                expect(comp.complaintsToShow).toEqual(addressedComplaints);
                expect(comp.showAddressedComplaints).toBe(true);
                break;
            case 5:
                // This filter option indicates that the user selected the part of the pie representing the number of open complaints
                // -> Only open complaints should be shown
                expect(comp.complaintsToShow).toEqual(openComplaints);
                expect(comp.showAddressedComplaints).toBe(false);
                break;
        }

        comp.resetFilterOptions();

        expect(comp.complaintsToShow).toEqual(openComplaints);
        expect(comp.filterOption).toBeUndefined();
    });

    function verifyNotCalled(...instances: MockInstance[]) {
        for (const spyInstance of instances) {
            expect(spyInstance).not.toHaveBeenCalled();
        }
    }
});
