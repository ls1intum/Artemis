import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';

import { CourseRequestsComponent } from 'app/core/admin/course-requests/course-requests.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseRequest, CourseRequestStatus } from 'app/core/shared/entities/course-request.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

describe('CourseRequestsComponent', () => {
    let component: CourseRequestsComponent;
    let courseRequestService: jest.Mocked<CourseRequestService>;
    let alertService: jest.Mocked<AlertService>;
    let modalService: NgbModal;

    const mockRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.PENDING,
    };

    const mockAcceptedRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.ACCEPTED,
        createdCourseId: 100,
    };

    const mockRejectedRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.REJECTED,
        decisionReason: 'Not approved',
    };

    beforeEach(async () => {
        courseRequestService = {
            create: jest.fn(),
            findAllForAdmin: jest.fn(),
            acceptRequest: jest.fn(),
            rejectRequest: jest.fn(),
        } as unknown as jest.Mocked<CourseRequestService>;

        alertService = {
            success: jest.fn(),
            error: jest.fn(),
        } as unknown as jest.Mocked<AlertService>;

        await TestBed.configureTestingModule({
            imports: [CourseRequestsComponent, HttpClientTestingModule, TranslateModule.forRoot()],
            providers: [
                { provide: CourseRequestService, useValue: courseRequestService },
                { provide: AlertService, useValue: alertService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        modalService = TestBed.inject(NgbModal);
        courseRequestService.findAllForAdmin.mockReturnValue(of([mockRequest]));

        const fixture = TestBed.createComponent(CourseRequestsComponent);
        component = fixture.componentInstance;
    });

    describe('ngOnInit', () => {
        it('should load requests on init', () => {
            component.ngOnInit();

            expect(courseRequestService.findAllForAdmin).toHaveBeenCalled();
            expect(component.requests).toEqual([mockRequest]);
            expect(component.loading).toBeFalse();
        });
    });

    describe('load', () => {
        it('should set loading to false after fetching', () => {
            component.load();

            expect(component.loading).toBeFalse();
            expect(courseRequestService.findAllForAdmin).toHaveBeenCalled();
        });

        it('should handle error when loading fails', () => {
            const error = { status: 500, message: 'Server error' };
            courseRequestService.findAllForAdmin.mockReturnValue(throwError(() => error));

            component.load();

            expect(component.loading).toBeFalse();
        });
    });

    describe('accept', () => {
        it('should accept a request and update the list', () => {
            courseRequestService.acceptRequest.mockReturnValue(of(mockAcceptedRequest));
            component.requests = [mockRequest];

            component.accept(mockRequest);

            expect(courseRequestService.acceptRequest).toHaveBeenCalledWith(1);
            expect(component.requests[0].status).toBe(CourseRequestStatus.ACCEPTED);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.acceptSuccess', { title: 'Test Course', shortName: 'TC' });
        });

        it('should not call service if request has no id', () => {
            const requestWithoutId: CourseRequest = { title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' };

            component.accept(requestWithoutId);

            expect(courseRequestService.acceptRequest).not.toHaveBeenCalled();
        });

        it('should handle error when accepting fails', () => {
            const error = { status: 500, message: 'Server error' };
            courseRequestService.acceptRequest.mockReturnValue(throwError(() => error));

            component.accept(mockRequest);

            expect(courseRequestService.acceptRequest).toHaveBeenCalledWith(1);
        });
    });

    describe('openRejectModal', () => {
        it('should open modal and set selected request', () => {
            const openSpy = jest.spyOn(modalService, 'open');
            const mockContent = {};

            component.openRejectModal(mockContent, mockRequest);

            expect(component.selectedRequest).toBe(mockRequest);
            expect(component.decisionReason).toBe('');
            expect(component.reasonInvalid).toBeFalse();
            expect(openSpy).toHaveBeenCalledWith(mockContent, { size: 'lg' });
        });
    });

    describe('reject', () => {
        beforeEach(() => {
            component.selectedRequest = mockRequest;
            component.modalRef = { close: jest.fn() } as unknown as NgbModalRef;
        });

        it('should reject a request with a reason', () => {
            courseRequestService.rejectRequest.mockReturnValue(of(mockRejectedRequest));
            component.requests = [mockRequest];
            component.decisionReason = 'Not approved';

            component.reject();

            expect(courseRequestService.rejectRequest).toHaveBeenCalledWith(1, 'Not approved');
            expect(component.requests[0].status).toBe(CourseRequestStatus.REJECTED);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.rejectSuccess', { title: 'Test Course' });
            expect(component.modalRef?.close).toHaveBeenCalled();
            expect(component.reasonInvalid).toBeFalse();
            expect(component.selectedRequest).toBeUndefined();
        });

        it('should set reasonInvalid when reason is empty', () => {
            component.decisionReason = '';

            component.reject();

            expect(component.reasonInvalid).toBeTrue();
            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should set reasonInvalid when reason is only whitespace', () => {
            component.decisionReason = '   ';

            component.reject();

            expect(component.reasonInvalid).toBeTrue();
            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should not call service if selectedRequest has no id', () => {
            component.selectedRequest = { title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' };
            component.decisionReason = 'Valid reason';

            component.reject();

            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should not call service if selectedRequest is undefined', () => {
            component.selectedRequest = undefined;
            component.decisionReason = 'Valid reason';

            component.reject();

            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should handle error when rejecting fails', () => {
            const error = { status: 500, message: 'Server error' };
            courseRequestService.rejectRequest.mockReturnValue(throwError(() => error));
            component.decisionReason = 'Not approved';

            component.reject();

            expect(courseRequestService.rejectRequest).toHaveBeenCalledWith(1, 'Not approved');
        });
    });

    describe('badgeClass', () => {
        it('should return bg-success for ACCEPTED status', () => {
            expect(component.badgeClass(CourseRequestStatus.ACCEPTED)).toBe('bg-success');
        });

        it('should return bg-danger for REJECTED status', () => {
            expect(component.badgeClass(CourseRequestStatus.REJECTED)).toBe('bg-danger');
        });

        it('should return bg-secondary for PENDING status', () => {
            expect(component.badgeClass(CourseRequestStatus.PENDING)).toBe('bg-secondary');
        });

        it('should return bg-secondary for undefined status', () => {
            expect(component.badgeClass(undefined)).toBe('bg-secondary');
        });
    });
});
