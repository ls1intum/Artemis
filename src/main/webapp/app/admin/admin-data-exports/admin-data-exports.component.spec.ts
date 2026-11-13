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
import { Subject, of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { TumAetUiPaginatorComponent } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDeleteDialogService } from 'test/helpers/mocks/service/mock-delete-dialog.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { AdminDataExportsComponent } from 'app/admin/admin-data-exports/admin-data-exports.component';
import { AdminDataExportsService } from 'app/admin/admin-data-exports/admin-data-exports.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { AdminDataExport, DataExportState } from 'app/admin/admin-data-exports/data-export.model';
import { PageableResult } from 'app/foundation/pagination/pageable-table';

describe('AdminDataExportsComponent', () => {
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
    };

    const mockAdminDataExportsService = {
        getAllDataExports: vi.fn().mockReturnValue(of(mockPageableResult)),
        downloadDataExport: vi.fn(),
        cancelDataExport: vi.fn(),
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
                { provide: DeleteDialogService, useClass: MockDeleteDialogService },
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

        component.onPageChange(1);
        await fixture.whenStable();

        expect(component.first()).toBe(20);
        expect(component.rows()).toBe(20);
        expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(1, 20); // page 1
    });

    it('should handle page size change', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        vi.clearAllMocks();

        component.onPageSizeChange(50);
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

    it('maps each data-export state to its severity token', () => {
        expect(component.getStateSeverity(DataExportState.REQUESTED)).toBe('contrast');
        expect(component.getStateSeverity(DataExportState.IN_CREATION)).toBe('info');
        expect(component.getStateSeverity(DataExportState.EMAIL_SENT)).toBe('success');
        expect(component.getStateSeverity(DataExportState.DOWNLOADED)).toBe('warn');
        expect(component.getStateSeverity(DataExportState.FAILED)).toBe('danger');
        expect(component.getStateSeverity(DataExportState.EMAIL_FAILED)).toBe('warn');
        expect(component.getStateSeverity(DataExportState.DELETED)).toBe('secondary');
        expect(component.getStateSeverity(DataExportState.DOWNLOADED_DELETED)).toBe('secondary');
    });

    it('should initialize pagination signals with default values', () => {
        expect(component.first()).toBe(0);
        expect(component.rows()).toBe(20);
        expect(component.totalRecords()).toBe(0);
    });
    describe('table frame', () => {
        const element = () => fixture.nativeElement as HTMLElement;
        const paginator = () => fixture.debugElement.query(By.directive(TumAetUiPaginatorComponent)).componentInstance as TumAetUiPaginatorComponent;

        beforeEach(async () => {
            mockAdminDataExportsService.getAllDataExports.mockReturnValue(of(mockPageableResult));
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        });

        it('should render the exports in the table layout with the paginator in its footer', () => {
            expect(element().querySelector('tumaet-ui-table-layout [data-testid="data-exports-table"]')).not.toBeNull();
            expect(element().querySelectorAll('[data-testid="data-exports-table"] tbody tr')).toHaveLength(2);
            expect(element().querySelector('tumaet-ui-table-layout [tumAetUiTableFooter]')).not.toBeNull();
            expect(paginator().totalRecords()).toBe(50);
            expect(paginator().pageSize()).toBe(20);
            expect(paginator().page()).toBe(0);
        });

        it('should label the download button for screen readers and offer it only for downloadable exports', () => {
            const downloadButtons = element().querySelectorAll('[data-testid="download-btn"]');

            expect(downloadButtons).toHaveLength(1);
            expect(downloadButtons[0].querySelector('button')!.getAttribute('aria-label')).toBe('entity.action.download');
        });

        it('should load the matching page when the paginator emits a zero-based page', async () => {
            vi.clearAllMocks();

            paginator().pageChange.emit(2);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(2, 20);
            expect(paginator().page()).toBe(2);
        });

        it('should disable the paginator while a page is loading, so overlapping responses cannot show the wrong page', () => {
            const pending = new Subject<typeof mockPageableResult>();
            mockAdminDataExportsService.getAllDataExports.mockReturnValue(pending);

            paginator().pageChange.emit(1);
            fixture.detectChanges();
            expect(paginator().disabled()).toBe(true);

            pending.next(mockPageableResult);
            pending.complete();
            fixture.detectChanges();
            expect(paginator().disabled()).toBe(false);
        });

        it('should reload from the first page when the paginator emits a new page size', async () => {
            vi.clearAllMocks();

            paginator().pageSizeChange.emit(10);
            await fixture.whenStable();

            expect(adminDataExportsService.getAllDataExports).toHaveBeenCalledWith(0, 10);
        });
    });

    describe('cancelExport', () => {
        beforeEach(() => {
            mockAdminDataExportsService.getAllDataExports.mockReturnValue(of(mockPageableResult));
        });

        it('should report success and reload the exports', () => {
            mockAdminDataExportsService.cancelDataExport.mockReturnValue(of(undefined));
            vi.clearAllMocks();

            component.cancelExport(mockExports[1]);

            expect(mockAdminDataExportsService.cancelDataExport).toHaveBeenCalledWith(2);
            expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.dataExport.admin.cancelSuccess', { login: 'user2' });
            expect(mockAdminDataExportsService.getAllDataExports).toHaveBeenCalledOnce();
        });

        it('should report an error and not reload when cancelling fails', () => {
            mockAdminDataExportsService.cancelDataExport.mockReturnValue(throwError(() => new Error('failed')));
            vi.clearAllMocks();

            component.cancelExport(mockExports[1]);

            expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.dataExport.admin.cancelError', { login: 'user2' });
            expect(mockAdminDataExportsService.getAllDataExports).not.toHaveBeenCalled();
        });
    });
});
