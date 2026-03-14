/**
 * Vitest tests for CourseRequestsComponent.
 * Tests the admin view for managing course creation requests including
 * reject functionality and form validation.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';

import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { CourseRequestsComponent } from 'app/core/admin/course-requests/course-requests.component';
import { AcceptCourseRequestModalComponent } from 'app/core/admin/course-requests/accept-course-request-modal.component';
import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseRequest, CourseRequestStatus, CourseRequestsAdminOverview } from 'app/core/shared/entities/course-request.model';

@Component({ selector: 'jhi-accept-course-request-modal', template: '' })
class MockAcceptCourseRequestModalComponent {
    open = vi.fn();
}

describe('CourseRequestsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: CourseRequestsComponent;
    let courseRequestService: CourseRequestService;
    let alertService: AlertService;

    /** Sample pending course request for testing */
    const mockRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
        testCourse: false,
        reason: 'Test reason',
        status: CourseRequestStatus.PENDING,
    };

    /** Sample rejected course request */
    const mockRejectedRequest: CourseRequest = {
        id: 1,
        title: 'Test Course',
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
        getInstructorCourses: vi.fn(),
    };

    /** Mock AlertService with spy functions */
    const mockAlertService = {
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
    };

    beforeEach(async () => {
        vi.restoreAllMocks();
        vi.clearAllMocks();

        await TestBed.configureTestingModule({
            imports: [CourseRequestsComponent, TranslateModule.forRoot(), OwlNativeDateTimeModule],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: CourseRequestService, useValue: mockCourseRequestService },
                { provide: AlertService, useValue: mockAlertService },
            ],
        })
            .overrideComponent(CourseRequestsComponent, {
                remove: { imports: [AcceptCourseRequestModalComponent] },
                add: { imports: [MockAcceptCourseRequestModalComponent] },
            })
            .compileComponents();

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

    describe('openRejectModal', () => {
        it('should open modal and set selected request', () => {
            component.openRejectModal(mockRequest);

            expect(component.selectedRequest()).toBe(mockRequest);
            expect(component.decisionReason()).toBe('');
            expect(component.reasonInvalid()).toBe(false);
            expect(component.rejectModalVisible()).toBe(true);
        });
    });

    describe('reject', () => {
        beforeEach(() => {
            component.selectedRequest.set(mockRequest);
            component.rejectModalVisible.set(true);
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
            expect(component.rejectModalVisible()).toBe(false);
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
            component.selectedRequest.set({ title: 'Test', testCourse: false, reason: 'reason' });
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
});
