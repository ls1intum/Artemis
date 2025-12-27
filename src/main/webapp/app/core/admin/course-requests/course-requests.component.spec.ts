/**
 * Vitest tests for CourseRequestsComponent.
 * Tests the admin view for managing course creation requests including
 * accept, reject, edit functionality and form validation.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';

import { CourseRequestsComponent } from 'app/core/admin/course-requests/course-requests.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseRequest, CourseRequestStatus, CourseRequestsAdminOverview } from 'app/core/shared/entities/course-request.model';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';

describe('CourseRequestsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseRequestsComponent;
    let courseRequestService: CourseRequestService;
    let alertService: AlertService;
    let modalService: NgbModal;

    /** Sample pending course request for testing */
    const mockRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.PENDING,
    };

    /** Sample accepted course request */
    const mockAcceptedRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.ACCEPTED,
        createdCourseId: 100,
    };

    /** Sample rejected course request */
    const mockRejectedRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        shortName: 'TC',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.REJECTED,
        decisionReason: 'Not approved',
    };

    /** Mock CourseRequestService with spy functions */
    const mockCourseRequestService = {
        create: vi.fn(),
        findAdminOverview: vi.fn(),
        acceptRequest: vi.fn(),
        rejectRequest: vi.fn(),
        updateRequest: vi.fn(),
    };

    /** Mock AlertService with spy functions */
    const mockAlertService = {
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
    };

    beforeEach(async () => {
        vi.clearAllMocks();

        await TestBed.configureTestingModule({
            imports: [CourseRequestsComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: CourseRequestService, useValue: mockCourseRequestService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        modalService = TestBed.inject(NgbModal);
        courseRequestService = TestBed.inject(CourseRequestService);
        alertService = TestBed.inject(AlertService);

        // Default mock for initial load
        const mockOverview: CourseRequestsAdminOverview = {
            pendingRequests: [mockRequest],
            decidedRequests: [],
            totalDecidedCount: 0,
        };
        mockCourseRequestService.findAdminOverview.mockReturnValue(of(mockOverview));

        const fixture = TestBed.createComponent(CourseRequestsComponent);
        component = fixture.componentInstance;
    });

    describe('ngOnInit', () => {
        it('should load requests on init', () => {
            component.ngOnInit();

            expect(courseRequestService.findAdminOverview).toHaveBeenCalled();
            expect(component.pendingRequests()).toEqual([mockRequest]);
            expect(component.decidedRequests()).toEqual([]);
            expect(component.loading()).toBe(false);
        });
    });

    describe('load', () => {
        it('should set loading to false after fetching', () => {
            component.load();

            expect(component.loading()).toBe(false);
            expect(courseRequestService.findAdminOverview).toHaveBeenCalled();
        });

        it('should handle error when loading fails', () => {
            const error = { status: 500, message: 'Server error' };
            mockCourseRequestService.findAdminOverview.mockReturnValue(throwError(() => error));

            component.load();

            expect(component.loading()).toBe(false);
        });
    });

    describe('accept', () => {
        it('should accept a request and move it to decided list', () => {
            mockCourseRequestService.acceptRequest.mockReturnValue(of(mockAcceptedRequest));
            component.pendingRequests.set([mockRequest]);
            component.decidedRequests.set([]);
            component.totalDecidedCount.set(0);

            component.accept(mockRequest);

            expect(courseRequestService.acceptRequest).toHaveBeenCalledWith(1);
            expect(component.pendingRequests()).toHaveLength(0);
            expect(component.decidedRequests()[0].status).toBe(CourseRequestStatus.ACCEPTED);
            expect(component.totalDecidedCount()).toBe(1);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.acceptSuccess', { title: 'Test Course', shortName: 'TC' });
        });

        it('should not call service if request has no id', () => {
            const requestWithoutId: CourseRequest = { title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' };

            component.accept(requestWithoutId);

            expect(courseRequestService.acceptRequest).not.toHaveBeenCalled();
        });

        it('should handle error when accepting fails', () => {
            const error = { status: 500, message: 'Server error' };
            mockCourseRequestService.acceptRequest.mockReturnValue(throwError(() => error));

            component.accept(mockRequest);

            expect(courseRequestService.acceptRequest).toHaveBeenCalledWith(1);
        });
    });

    describe('openRejectModal', () => {
        it('should open modal and set selected request', () => {
            const openSpy = vi.spyOn(modalService, 'open');
            const mockContent = {};

            component.openRejectModal(mockContent, mockRequest);

            expect(component.selectedRequest()).toBe(mockRequest);
            expect(component.decisionReason()).toBe('');
            expect(component.reasonInvalid()).toBe(false);
            expect(openSpy).toHaveBeenCalledWith(mockContent, { size: 'lg' });
        });
    });

    describe('reject', () => {
        beforeEach(() => {
            component.selectedRequest.set(mockRequest);
            component.modalRef = { close: vi.fn() } as unknown as NgbModalRef;
        });

        it('should reject a request with a reason and move it to decided list', () => {
            mockCourseRequestService.rejectRequest.mockReturnValue(of(mockRejectedRequest));
            component.pendingRequests.set([mockRequest]);
            component.decidedRequests.set([]);
            component.totalDecidedCount.set(0);
            component.decisionReason.set('Not approved');

            component.reject();

            expect(courseRequestService.rejectRequest).toHaveBeenCalledWith(1, 'Not approved');
            expect(component.pendingRequests()).toHaveLength(0);
            expect(component.decidedRequests()[0].status).toBe(CourseRequestStatus.REJECTED);
            expect(component.totalDecidedCount()).toBe(1);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.rejectSuccess', { title: 'Test Course' });
            expect(component.modalRef?.close).toHaveBeenCalled();
            expect(component.reasonInvalid()).toBe(false);
            expect(component.selectedRequest()).toBeUndefined();
        });

        it('should set reasonInvalid when reason is empty', () => {
            component.decisionReason.set('');

            component.reject();

            expect(component.reasonInvalid()).toBe(true);
            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should set reasonInvalid when reason is only whitespace', () => {
            component.decisionReason.set('   ');

            component.reject();

            expect(component.reasonInvalid()).toBe(true);
            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should not call service if selectedRequest has no id', () => {
            component.selectedRequest.set({ title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' });
            component.decisionReason.set('Valid reason');

            component.reject();

            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should not call service if selectedRequest is undefined', () => {
            component.selectedRequest.set(undefined);
            component.decisionReason.set('Valid reason');

            component.reject();

            expect(courseRequestService.rejectRequest).not.toHaveBeenCalled();
        });

        it('should handle error when rejecting fails', () => {
            const error = { status: 500, message: 'Server error' };
            mockCourseRequestService.rejectRequest.mockReturnValue(throwError(() => error));
            component.decisionReason.set('Not approved');

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
            const openSpy = vi.spyOn(modalService, 'open');
            const mockContent = {};
            const requestWithDates: CourseRequest = {
                ...mockRequest,
                semester: 'WS25/26',
                startDate: dayjs('2025-10-01'),
                endDate: dayjs('2026-03-31'),
            };

            component.openEditModal(mockContent, requestWithDates);

            expect(component.selectedRequest()).toBe(requestWithDates);
            expect(component.editDateRangeInvalid()).toBe(false);
            expect(component.isSubmittingEdit()).toBe(false);
            expect(component.editForm.get('title')?.value).toBe('Test Course');
            expect(component.editForm.get('shortName')?.value).toBe('TC');
            expect(component.editForm.get('semester')?.value).toBe('WS25/26');
            expect(component.editForm.get('reason')?.value).toBe('Test reason');
            expect(openSpy).toHaveBeenCalledWith(mockContent, { size: 'lg' });
        });
    });

    describe('saveEdit', () => {
        beforeEach(() => {
            component.selectedRequest.set(mockRequest);
            component.modalRef = { close: vi.fn() } as unknown as NgbModalRef;
        });

        it('should update request and refresh list on success', () => {
            const updatedRequest: CourseRequest = { ...mockRequest, title: 'Updated Course' };
            mockCourseRequestService.updateRequest.mockReturnValue(of(updatedRequest));
            component.pendingRequests.set([mockRequest]);
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
            expect(component.pendingRequests()[0].title).toBe('Updated Course');
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.courseRequest.admin.editSuccess');
            expect(component.modalRef?.close).toHaveBeenCalled();
            expect(component.isSubmittingEdit()).toBe(false);
            expect(component.selectedRequest()).toBeUndefined();
        });

        it('should not submit when form is invalid', () => {
            component.editForm.patchValue({
                title: '', // Invalid - required
                shortName: 'TC',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(courseRequestService.updateRequest).not.toHaveBeenCalled();
            expect(component.editForm.get('title')?.touched).toBe(true);
        });

        it('should not submit when selectedRequest has no id', () => {
            component.selectedRequest.set({ title: 'Test', shortName: 'T', testCourse: false, reason: 'reason' });
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

            expect(component.editDateRangeInvalid()).toBe(true);
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
            mockCourseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'EXISTING',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.form.shortNameNotUnique', { suggestedShortName });
            expect(component.editForm.get('shortName')?.value).toBe(suggestedShortName);
            expect(component.isSubmittingEdit()).toBe(false);
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
            mockCourseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
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
            mockCourseRequestService.updateRequest.mockReturnValue(throwError(() => errorResponse));
            component.editForm.patchValue({
                title: 'Test Course',
                shortName: 'TC1',
                semester: 'WS25/26',
                reason: 'Valid reason',
            });

            component.saveEdit();

            expect(component.isSubmittingEdit()).toBe(false);
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
            mockCourseRequestService.acceptRequest.mockReturnValue(throwError(() => errorResponse));

            component.accept(mockRequest);

            expect(alertService.warning).toHaveBeenCalledWith('artemisApp.courseRequest.admin.shortNameConflict', {
                suggestedShortName,
                shortName: 'TC',
            });
        });
    });
});
