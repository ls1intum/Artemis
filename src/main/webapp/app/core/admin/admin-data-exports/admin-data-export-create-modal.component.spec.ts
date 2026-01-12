/**
 * Vitest tests for AdminDataExportCreateModalComponent.
 *
 * Tests cover:
 * - Component creation
 * - Dialog visibility control via open() and cancel()
 * - Validation (no submission when no user selected)
 * - Submission with scheduled and immediate options
 * - Error handling during submission
 * - Callback invocation on success
 * - Loading state management
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AdminDataExportCreateModalComponent } from 'app/core/admin/admin-data-exports/admin-data-export-create-modal.component';
import { AdminDataExportsService } from 'app/core/admin/admin-data-exports/admin-data-exports.service';
import { AlertService } from 'app/shared/service/alert.service';
import { DataExport, DataExportState } from 'app/core/shared/entities/data-export.model';

describe('AdminDataExportCreateModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AdminDataExportCreateModalComponent;
    let fixture: ComponentFixture<AdminDataExportCreateModalComponent>;
    let adminDataExportsService: AdminDataExportsService;
    let alertService: AlertService;

    const mockDataExport: DataExport = {
        id: 1,
        dataExportState: DataExportState.REQUESTED,
    };

    const mockAdminDataExportsService = {
        requestDataExportForUser: vi.fn().mockReturnValue(of(mockDataExport)),
    };

    const mockAlertService = {
        success: vi.fn(),
        error: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();

        TestBed.configureTestingModule({
            imports: [AdminDataExportCreateModalComponent],
            providers: [
                { provide: AdminDataExportsService, useValue: mockAdminDataExportsService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });

        fixture = TestBed.createComponent(AdminDataExportCreateModalComponent);
        component = fixture.componentInstance;
        adminDataExportsService = TestBed.inject(AdminDataExportsService);
        alertService = TestBed.inject(AlertService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should open dialog and reset state', () => {
        component.selectedUserLogin.set('olduser');
        component.executeNow.set(true);
        component.isSubmitting.set(true);

        component.open();

        expect(component.visible()).toBe(true);
        expect(component.selectedUserLogin()).toBe('');
        expect(component.executeNow()).toBe(false);
        expect(component.isSubmitting()).toBe(false);
    });

    it('should close dialog on cancel', () => {
        component.visible.set(true);
        component.cancel();
        expect(component.visible()).toBe(false);
    });

    it('should not submit when no user is selected', () => {
        component.selectedUserLogin.set('');
        component.submit();
        expect(adminDataExportsService.requestDataExportForUser).not.toHaveBeenCalled();
    });

    it('should submit with schedule option', async () => {
        component.selectedUserLogin.set('testuser');
        component.executeNow.set(false);
        component.visible.set(true);

        component.submit();
        await fixture.whenStable();

        expect(adminDataExportsService.requestDataExportForUser).toHaveBeenCalledWith('testuser', false);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.dataExport.admin.createSuccessScheduled', { login: 'testuser' });
        expect(component.visible()).toBe(false);
    });

    it('should submit with execute now option', async () => {
        component.selectedUserLogin.set('testuser');
        component.executeNow.set(true);
        component.visible.set(true);

        component.submit();
        await fixture.whenStable();

        expect(adminDataExportsService.requestDataExportForUser).toHaveBeenCalledWith('testuser', true);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.dataExport.admin.createSuccessImmediate', { login: 'testuser' });
        expect(component.visible()).toBe(false);
    });

    it('should invoke onSuccess callback after successful submission', async () => {
        const onSuccessSpy = vi.fn();
        component.open(onSuccessSpy);
        component.selectedUserLogin.set('testuser');

        component.submit();
        await fixture.whenStable();

        expect(onSuccessSpy).toHaveBeenCalled();
    });

    it('should show error on submit failure', async () => {
        mockAdminDataExportsService.requestDataExportForUser.mockReturnValue(throwError(() => new Error('Network error')));
        component.selectedUserLogin.set('testuser');
        component.executeNow.set(false);
        component.visible.set(true);

        component.submit();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.dataExport.admin.createError', { login: 'testuser' });
        expect(component.isSubmitting()).toBe(false);
        expect(component.visible()).toBe(true); // Dialog stays open on error
    });

    it('should initialize with isSubmitting as false', () => {
        expect(component.isSubmitting()).toBe(false);
    });

    it('should initialize with visible as false', () => {
        expect(component.visible()).toBe(false);
    });

    it('should reset isSubmitting on error', async () => {
        mockAdminDataExportsService.requestDataExportForUser.mockReturnValue(throwError(() => new Error('Network error')));
        component.selectedUserLogin.set('testuser');

        component.submit();
        await fixture.whenStable();

        expect(component.isSubmitting()).toBe(false);
    });
});
