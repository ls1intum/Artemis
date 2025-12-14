import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';

import { CourseRequestsComponent } from 'app/core/admin/course-requests/course-requests.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseRequest, CourseRequestStatus, CourseRequestsAdminOverview } from 'app/core/shared/entities/course-request.model';
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
            findAdminOverview: jest.fn(),
            acceptRequest: jest.fn(),
            rejectRequest: jest.fn(),
            updateRequest: jest.fn(),
        } as unknown as jest.Mocked<CourseRequestService>;

        alertService = {
            success: jest.fn(),
            error: jest.fn(),
            warning: jest.fn(),
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
        const mockOverview: CourseRequestsAdminOverview = {
            pendingRequests: [mockRequest],
            decidedRequests: [],
            totalDecidedCount: 0,
        };
        courseRequestService.findAdminOverview.mockReturnValue(of(mockOverview));

        const fixture = TestBed.createComponent(CourseRequestsComponent);
        component = fixture.componentInstance;
    });

    describe('ngOnInit', () => {
        it('should load requests on init', () => {
            component.ngOnInit();

            expect(courseRequestService.findAdminOverview).toHaveBeenCalled();
            expect(component.pendingRequests).toEqual([mockRequest]);
            expect(component.decidedRequests).toEqual([]);
            expect(component.loading).toBeFalse();
        });
    });

    describe('load', () => {
        it('should set loading to false after fetching', () => {
            component.load();

            expect(component.loading).toBeFalse();
            expect(courseRequestService.findAdminOverview).toHaveBeenCalled();
        });

        it('should handle error when loading fails', () => {
            const error = { status: 500, message: 'Server error' };
            courseRequestService.findAdminOverview.mockReturnValue(throwError(() => error));

            component.load();

            expect(component.loading).toBeFalse();
        });
    });

    describe('accept', () => {
        it('should accept a request and move it to decided list', () => {
            courseRequestService.acceptRequest.mockReturnValue(of(mockAcceptedRequest));
            component.pendingRequests = [mockRequest];
            component.decidedRequests = [];
            component.totalDecidedCount = 0;

            component.accept(mockRequest);

            expect(courseRequestService.acceptRequest).toHaveBeenCalledWith(1);
            expect(component.pendingRequests).toHaveLength(0);
            expect(component.decidedRequests[0].status).toBe(CourseRequestStatus.ACCEPTED);
            expect(component.totalDecidedCount).toBe(1);
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

        it('should reject a request with a reason and move it to decided list', () => {
            courseRequestService.rejectRequest.mockReturnValue(of(mockRejectedRequest));
            component.pendingRequests = [mockRequest];
            component.decidedRequests = [];
            component.totalDecidedCount = 0;
            component.decisionReason = 'Not approved';

            component.reject();

            expect(courseRequestService.rejectRequest).toHaveBeenCalledWith(1, 'Not approved');
            expect(component.pendingRequests).toHaveLength(0);
            expect(component.decidedRequests[0].status).toBe(CourseRequestStatus.REJECTED);
            expect(component.totalDecidedCount).toBe(1);
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

    describe('formatInstructorCount', () => {
        it('should return "No" for undefined count', () => {
            expect(component.formatInstructorCount(undefined)).toBe('No');
        });

        it('should return "No" for zero count', () => {
            expect(component.formatInstructorCount(0)).toBe('No');
        });

        it('should return "Yes (count)" for positive count', () => {
            expect(component.formatInstructorCount(3)).toBe('Yes (3)');
        });
    });

    describe('openEditModal', () => {
        it('should open modal and populate form with request data', () => {
            const openSpy = jest.spyOn(modalService, 'open');
            const mockContent = {};
            const requestWithDates: CourseRequest = {
                ...mockRequest,
                semester: 'WS25/26',
                startDate: dayjs('2025-10-01'),
                endDate: dayjs('2026-03-31'),
            };

            component.openEditModal(mockContent, requestWithDates);

            expect(component.selectedRequest).toBe(requestWithDates);
            expect(component.editDateRangeInvalid).toBeFalse();
            expect(component.isSubmittingEdit).toBeFalse();
            expect(component.editForm.get('title')?.value).toBe('Test Course');
            expect(component.editForm.get('shortName')?.value).toBe('TC');
            expect(component.editForm.get('semester')?.value).toBe('WS25/26');
            expect(component.editForm.get('reason')?.value).toBe('Test reason');
            expect(openSpy).toHaveBeenCalledWith(mockContent, { size: 'lg' });
        });
    });

    describe('saveEdit', () => {
        beforeEach(() => {
            component.selectedRequest = mockRequest;
            component.modalRef = { close: jest.fn() } as unknown as NgbModalRef;
        });

        it('should update request and refresh list on success', () => {
            const updatedRequest: CourseRequest = { ...mockRequest, title: 'Updated Course' };
            courseRequestService.updateRequest.mockReturnValue(of(updatedRequest));
            component.pendingRequests = [mockRequest];
            component.editForm.patchValue({
                title: 'Updated Course',
                shortName: 'UC1',
                semester: 'WS25/26',
                reason: 'Updated reason',
            });

            component.saveEdit();

            expect(courseRequestService.updateRequest).toHaveBeenCalledWith(
                1,
                expect.objectContaining({
                    title: 'Updated Course',
                    shortName: 'UC1',
                }),
            );
            expect(component.pendingRequests[0].title).toBe('Updated Course');
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.editSuccess');
            expect(component.modalRef?.close).toHaveBeenCalled();
            expect(component.isSubmittingEdit).toBeFalse();
            expect(component.selectedRequest).toBeUndefined();
        });

        it('should not submit when form is invalid', () => {
            component.editForm.patchValue({
                title: '', // Invalid - required
                shortName: 'TC',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(courseRequestService.updateRequest).not.toHaveBeenCalled();
            expect(component.editForm.get('title')?.touched).toBeTrue();
        });

        it('should not submit when selectedRequest has no id', () => {
            component.selectedRequest = { title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' };
            component.editForm.patchValue({
                title: 'Test',
                shortName: 'TST',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(courseRequestService.updateRequest).not.toHaveBeenCalled();
        });

        it('should set dateRangeInvalid when end date is before start date', () => {
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'TC1',
                semester: 'WS25/26',
                reason: 'Valid reason',
                startDate: dayjs('2025-02-01'),
                endDate: dayjs('2025-01-01'),
            });

            component.saveEdit();

            expect(component.editDateRangeInvalid).toBeTrue();
            expect(courseRequestService.updateRequest).not.toHaveBeenCalled();
        });

        it('should handle short name conflict error and apply suggested short name', () => {
            const suggestedShortName = 'TC2025';
            const errorResponse = new HttpErrorResponse({
                error: {
                    errorKey: 'courseShortNameExists',
                    params: { suggestedShortName },
                },
                status: 400,
            });
            courseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'EXISTING',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName });
            expect(component.editForm.get('shortName')?.value).toBe(suggestedShortName);
            expect(component.isSubmittingEdit).toBeFalse();
        });

        it('should handle courseRequestShortNameExists error', () => {
            const suggestedShortName = 'TC2025';
            const errorResponse = new HttpErrorResponse({
                error: {
                    errorKey: 'courseRequestShortNameExists',
                    params: { suggestedShortName },
                },
                status: 400,
            });
            courseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'EXISTING',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName });
            expect(component.editForm.get('shortName')?.value).toBe(suggestedShortName);
        });

        it('should handle other errors using onError', () => {
            const errorResponse = new HttpErrorResponse({
                error: { message: 'Server error' },
                status: 500,
            });
            courseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'TC1',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(component.isSubmittingEdit).toBeFalse();
        });
    });

    describe('accept error handling', () => {
        it('should show warning for short name conflict on accept', () => {
            const suggestedShortName = 'TC2025';
            const errorResponse = new HttpErrorResponse({
                error: {
                    errorKey: 'courseShortNameExists',
                    params: { suggestedShortName },
                },
                status: 400,
            });
            courseRequestService.acceptRequest.mockReturnValue(throwError(() => errorResponse));

            component.accept(mockRequest);

            expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.admin.shortNameConflict', {
                suggestedShortName,
                shortName: 'TC',
            });
        });
    });
});
