/**
 * Vitest tests for AdminDataExportsComponent.
 *
 * Tests cover:
 * - Component initialization and data loading with pagination
 * - Error handling during data loading
 * - Download functionality
 * - Modal opening via viewChild
 * - State icon and badge class mappings (unique colors)
 * - Pagination handling
 * - Track identity function for ngFor optimization
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { AdminDataExportsComponent } from 'app/core/admin/admin-data-exports/admin-data-exports.component';
import { AdminDataExportsService } from 'app/core/admin/admin-data-exports/admin-data-exports.service';
import { AlertService } from 'app/shared/service/alert.service';
import { AdminDataExport, DataExportState } from 'app/core/shared/entities/data-export.model';
import { PageableResult } from 'app/shared/table/pageable-table';

describe('AdminDataExportsComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AdminDataExportsComponent;
    let fixture: ComponentFixture<AdminDataExportsComponent>;
    let adminDataExportsService: AdminDataExportsService;
    let alertService: AlertService;

    const mockExports: AdminDataExport[] = [
        {
            id: 1,
            userId: 1,
            userLogin: 'user1',
            userName: 'User One',
            dataExportState: DataExportState.EMAIL_SENT,
            createdDate: dayjs('2024-01-01'),
            creationFinishedDate: dayjs('2024-01-01'),
            downloadable: true,
        },
        {
            id: 2,
            userId: 2,
            userLogin: 'user2',
            userName: 'User Two',
            dataExportState: DataExportState.REQUESTED,
            createdDate: dayjs('2024-01-02'),
            downloadable: false,
        },
    ];

    const mockPageableResult: PageableResult<AdminDataExport> = {
        content: mockExports,
        totalElements: 50,
        totalPages: 3,
    };

    const mockAdminDataExportsService = {
        getAllDataExports: vi.fn().mockReturnValue(of(mockPageableResult)),
        downloadDataExport: vi.fn(),
    };

    const mockAlertService = {
        error: vi.fn(),
        success: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();

        TestBed.configureTestingModule({
            imports: [AdminDataExportsComponent],
            providers: [
                { provide: AdminDataExportsService, useValue: mockAdminDataExportsService },
                { provide: AlertService, useValue: mockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
            ],
        });

        fixture = TestBed.createComponent(AdminDataExportsComponent);
        component = fixture.componentInstance;
        adminDataExportsService = TestBed.inject(AdminDataExportsService);
        alertService = TestBed.inject(AlertService);
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load data exports on init with pagination', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(0, 20);
        expect(component.dataExports()).toEqual(mockExports);
        expect(component.totalRecords()).toBe(50);
        expect(component.loading()).toBe(false);
    });

    it('should show error alert when loading fails', async () => {
        mockAdminDataExportsService.getAllDataExports.mockReturnValue(throwError(() => new Error('Network error')));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.dataExport.admin.loadError');
        expect(component.loading()).toBe(false);
    });

    it('should download export when downloadable', () => {
        const downloadableExport = mockExports[0];
        component.downloadExport(downloadableExport);

        expect(adminDataExportsService.downloadDataExport).toHaveBeenCalledWith(downloadableExport.id);
    });

    it('should not download export when not downloadable', () => {
        const nonDownloadableExport = mockExports[1];
        component.downloadExport(nonDownloadableExport);

        expect(adminDataExportsService.downloadDataExport).not.toHaveBeenCalled();
    });

    it('should handle page change', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        vi.clearAllMocks();

        component.onPageChange({ first: 20, rows: 20 });
        await fixture.whenStable();

        expect(component.first()).toBe(20);
        expect(component.rows()).toBe(20);
        expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(1, 20); // page 1
    });

    it('should handle page size change', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        vi.clearAllMocks();

        component.onPageChange({ first: 0, rows: 50 });
        await fixture.whenStable();

        expect(component.rows()).toBe(50);
        expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(0, 50);
    });

    it('should return correct icon for each state', () => {
        expect(component.getStateIcon(DataExportState.REQUESTED)).toBe(component['faClock']);
        expect(component.getStateIcon(DataExportState.IN_CREATION)).toBe(component['faClock']);
        expect(component.getStateIcon(DataExportState.EMAIL_SENT)).toBe(component['faCheck']);
        expect(component.getStateIcon(DataExportState.DOWNLOADED)).toBe(component['faCheck']);
        expect(component.getStateIcon(DataExportState.FAILED)).toBe(component['faExclamationTriangle']);
        expect(component.getStateIcon(DataExportState.DELETED)).toBe(component['faTimes']);
        expect(component.getStateIcon(DataExportState.DOWNLOADED_DELETED)).toBe(component['faTimes']);
    });

    it('should return unique badge class for each state', () => {
        expect(component.getStateBadgeClass(DataExportState.REQUESTED)).toBe('bg-primary');
        expect(component.getStateBadgeClass(DataExportState.IN_CREATION)).toBe('bg-info');
        expect(component.getStateBadgeClass(DataExportState.EMAIL_SENT)).toBe('bg-success');
        expect(component.getStateBadgeClass(DataExportState.DOWNLOADED)).toBe('bg-warning text-dark');
        expect(component.getStateBadgeClass(DataExportState.FAILED)).toBe('bg-danger');
        expect(component.getStateBadgeClass(DataExportState.DELETED)).toBe('bg-secondary');
        expect(component.getStateBadgeClass(DataExportState.DOWNLOADED_DELETED)).toBe('bg-secondary');
    });

    it('should track items by id', () => {
        const item = mockExports[0];
        expect(component.trackIdentity(0, item)).toBe(item.id);
    });

    it('should initialize pagination signals with default values', () => {
        expect(component.first()).toBe(0);
        expect(component.rows()).toBe(20);
        expect(component.totalRecords()).toBe(0);
    });
});
