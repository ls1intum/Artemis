import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ListOfComplaintsComponent } from 'app/complaints/list-of-complaints/list-of-complaints.component';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { ComplaintService, EntityResponseTypeArray, IComplaintService } from 'app/complaints/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/shared/service/sort.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { of } from 'rxjs';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs';

describe('ListOfComplaintsComponent', () => {
    let fixture: ComponentFixture<ListOfComplaintsComponent>;
    let comp: ListOfComplaintsComponent;

    let complaintService: IComplaintService;

    let findAllByTutorIdForExerciseIdStub: jest.SpyInstance;
    let findAllByTutorIdForCourseIdStub: jest.SpyInstance;
    let findAllByExerciseIdStub: jest.SpyInstance;
    let findAllByCourseIdAndExamIdStub: jest.SpyInstance;
    let findAllByCourseIdStub: jest.SpyInstance;

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
            declarations: [ListOfComplaintsComponent, TranslatePipeMock, MockComponent(AlertComponent), MockComponent(FaIconComponent)],
            providers: [
                MockProvider(AlertService),
                MockProvider(SortService),
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ArtemisDatePipe, useClass: TranslatePipeMock },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ListOfComplaintsComponent);
                comp = fixture.componentInstance;

                complaintService = fixture.debugElement.injector.get(ComplaintService);

                findAllByTutorIdForExerciseIdStub = jest.spyOn(complaintService, 'findAllByTutorIdForExerciseId');
                findAllByTutorIdForCourseIdStub = jest.spyOn(complaintService, 'findAllByTutorIdForCourseId');
                findAllByExerciseIdStub = jest.spyOn(complaintService, 'findAllByExerciseId');
                findAllByCourseIdAndExamIdStub = jest.spyOn(complaintService, 'findAllByCourseIdAndExamId');
                findAllByCourseIdStub = jest.spyOn(complaintService, 'findAllByCourseId');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('loadComplaints', () => {
        it('find for tutor by exercise', () => {
            comp.tutorId = 12;
            comp.courseId = 34;
            comp.exerciseId = 56;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();
            expect(findAllByTutorIdForExerciseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForExerciseIdStub).toHaveBeenCalledWith(12, 56, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find for tutor by exam', () => {
            comp.tutorId = 12;
            comp.courseId = 34;
            comp.examId = 56;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledWith(12, 34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find for tutor by course', () => {
            comp.tutorId = 12;
            comp.courseId = 34;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByTutorIdForCourseIdStub).toHaveBeenCalledWith(12, 34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find general by exercise', () => {
            comp.courseId = 12;
            comp.exerciseId = 34;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();

            expect(findAllByExerciseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByExerciseIdStub).toHaveBeenCalledWith(34, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByTutorIdForCourseIdStub, findAllByCourseIdAndExamIdStub, findAllByCourseIdStub);
        });

        it('find general by exam', () => {
            comp.courseId = 12;
            comp.examId = 34;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();

            expect(findAllByCourseIdAndExamIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByCourseIdAndExamIdStub).toHaveBeenCalledWith(12, 34);
            verifyNotCalled(findAllByTutorIdForCourseIdStub, findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdStub);
        });

        it('find general by course', () => {
            comp.courseId = 12;
            comp.complaintType = ComplaintType.MORE_FEEDBACK;
            comp.loadComplaints();

            expect(findAllByCourseIdStub).toHaveBeenCalledTimes(1);
            expect(findAllByCourseIdStub).toHaveBeenCalledWith(12, ComplaintType.MORE_FEEDBACK);
            verifyNotCalled(findAllByTutorIdForExerciseIdStub, findAllByTutorIdForCourseIdStub, findAllByExerciseIdStub, findAllByCourseIdAndExamIdStub);
        });

        it('process complaints without student information', () => {
            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3] } as EntityResponseTypeArray));

            comp.loadComplaints();

            expect(comp.complaintsToShow).toIncludeSameMembers([complaint3]);
            expect(comp.hasStudentInformation).toBe(false);
        });

        it('process complaints with student information', () => {
            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint4] } as EntityResponseTypeArray));

            comp.loadComplaints();

            expect(comp.complaintsToShow).toIncludeSameMembers([complaint3, complaint4]);
            expect(comp.hasStudentInformation).toBe(true);

            findAllByCourseIdStub.mockReturnValue(of({ body: [complaint1, complaint2, complaint3, complaint5] } as EntityResponseTypeArray));

            comp.loadComplaints();

            expect(comp.complaintsToShow).toIncludeSameMembers([complaint3]);
            expect(comp.hasStudentInformation).toBe(true);
        });
    });

    describe('shouldHighlightComplaint', () => {
        it('should not highlight handled complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(1, 'hours'),
                accepted: true,
            } as Complaint;

            const result = comp.shouldHighlightComplaint(complaint);

            expect(result).toBe(false);
        });

        it('should not highlight recent complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days').add(1, 'seconds'),
            } as Complaint;

            const result = comp.shouldHighlightComplaint(complaint);

            expect(result).toBe(false);
        });

        it('should highlight old complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days'),
            } as Complaint;

            const result = comp.shouldHighlightComplaint(complaint);

            expect(result).toBe(true);
        });
    });

    function verifyNotCalled(...instances: jest.SpyInstance[]) {
        for (const spyInstance of instances) {
            expect(spyInstance).not.toHaveBeenCalled();
        }
    }
});
