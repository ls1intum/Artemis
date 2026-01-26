import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminDataExport, DataExportState } from 'app/core/shared/entities/data-export.model';
import { AdminDataExportsService } from 'app/core/admin/admin-data-exports/admin-data-exports.service';
import { faBan, faCheck, faClock, faDownload, faExclamationTriangle, faPlus, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AdminDataExportCreateModalComponent } from 'app/core/admin/admin-data-exports/admin-data-export-create-modal.component';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

/**
 * Admin component for managing user data exports in accordance with GDPR Art. 15.
 *
 * This component provides administrators with:
 * - A paginated table view of all data exports across all users
 * - Status indicators showing the current state of each export
 * - Download functionality for completed exports
 * - Ability to create new exports for any user
 *
 * The component uses Angular signals for reactive state management and
 * follows the OnPush change detection strategy for optimal performance.
 */
@Component({
    selector: 'jhi-admin-data-exports',
    templateUrl: './admin-data-exports.component.html',
    styleUrls: ['./admin-data-exports.component.scss'],
    imports: [
        TranslateDirective,
        FaIconComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        NgClass,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        FormsModule,
        AdminDataExportCreateModalComponent,
        PaginatorModule,
        DeleteButtonDirective,
    ],
})
export class AdminDataExportsComponent implements OnInit {
    private readonly adminDataExportsService = inject(AdminDataExportsService);
    private readonly alertService = inject(AlertService);

    /** Reference to the create modal component */
    readonly createModal = viewChild<AdminDataExportCreateModalComponent>('createModal');

    /** Signal containing the list of data exports for the current page */
    readonly dataExports = signal<AdminDataExport[]>([]);

    /** Signal indicating whether data is currently being loaded */
    readonly loading = signal<boolean>(false);

    /** Pagination state */
    readonly first = signal<number>(0);
    readonly rows = signal<number>(20);
    readonly totalRecords = signal<number>(0);

    // Icons for UI
    protected readonly faBan = faBan;
    protected readonly faDownload = faDownload;
    protected readonly faPlus = faPlus;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;
    protected readonly faClock = faClock;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    // State enum exposed to template for conditional rendering
    protected readonly DataExportState = DataExportState;

    ngOnInit(): void {
        this.loadDataExports();
    }

    /**
     * Loads data exports from the server for the current page and updates the component state.
     * Shows a loading indicator while fetching and displays an error alert on failure.
     */
    loadDataExports(): void {
        this.loading.set(true);
        const page = this.first() / this.rows();
        this.adminDataExportsService.getAllDataExports(page, this.rows()).subscribe({
            next: (result) => {
                this.dataExports.set(result.content);
                this.totalRecords.set(result.totalElements);
                this.loading.set(false);
            },
            error: () => {
                this.alertService.error('artemisApp.dataExport.admin.loadError');
                this.loading.set(false);
            },
        });
    }

    /**
     * Handles page change events from the paginator.
     *
     * @param event The paginator state containing first index and rows per page
     */
    onPageChange(event: PaginatorState): void {
        this.first.set(event.first ?? 0);
        this.rows.set(event.rows ?? 20);
        this.loadDataExports();
    }

    /**
     * Opens the modal dialog for creating a new data export.
     * The user search component allows selecting any user in the system.
     * After successful creation, the export list is automatically refreshed.
     */
    openCreateModal(): void {
        this.createModal()?.open(() => {
            this.loadDataExports();
        });
    }

    /**
     * Initiates download of a data export if it is downloadable.
     * The downloadable flag is determined by the server based on file availability
     * and export state (must be EMAIL_SENT or DOWNLOADED with non-null filePath).
     *
     * @param dataExport The export to download
     */
    downloadExport(dataExport: AdminDataExport): void {
        if (dataExport.downloadable) {
            this.adminDataExportsService.downloadDataExport(dataExport.id);
        }
    }

    /**
     * Returns the appropriate FontAwesome icon for a given export state.
     * - Clock: pending states (REQUESTED, IN_CREATION)
     * - Check: completed states (EMAIL_SENT, DOWNLOADED)
     * - Warning: failure states (FAILED, EMAIL_FAILED)
     * - Times: deleted states (DELETED, DOWNLOADED_DELETED)
     *
     * @param state The current state of the data export
     */
    getStateIcon(state: DataExportState) {
        switch (state) {
            case DataExportState.REQUESTED:
            case DataExportState.IN_CREATION:
                return this.faClock;
            case DataExportState.EMAIL_SENT:
            case DataExportState.DOWNLOADED:
                return this.faCheck;
            case DataExportState.FAILED:
            case DataExportState.EMAIL_FAILED:
                return this.faExclamationTriangle;
            default:
                return this.faTimes;
        }
    }

    /**
     * Returns the Bootstrap badge CSS class for a given export state.
     * Each state has a unique color for easy identification:
     * - Primary (blue): requested
     * - Info (cyan): in creation
     * - Success (green): ready (email sent)
     * - Warning (yellow): downloaded or notification failed
     * - Danger (red): failed
     * - Secondary (gray): deleted
     *
     * @param state The current state of the data export
     */
    getStateBadgeClass(state: DataExportState): string {
        switch (state) {
            case DataExportState.REQUESTED:
                return 'bg-primary';
            case DataExportState.IN_CREATION:
                return 'bg-info';
            case DataExportState.EMAIL_SENT:
                return 'bg-success';
            case DataExportState.DOWNLOADED:
                return 'bg-warning text-dark';
            case DataExportState.FAILED:
                return 'bg-danger';
            case DataExportState.EMAIL_FAILED:
                return 'bg-warning text-dark';
            case DataExportState.DELETED:
            case DataExportState.DOWNLOADED_DELETED:
                return 'bg-secondary';
            default:
                return 'bg-secondary';
        }
    }

    /**
     * Track function for ngFor to optimize rendering performance.
     *
     * @param index The index of the item in the array
     * @param item The data export item
     */
    trackIdentity(index: number, item: AdminDataExport): number {
        return item.id;
    }

    /**
     * Checks if a data export can be cancelled.
     * Only exports in REQUESTED or IN_CREATION state can be cancelled.
     *
     * @param dataExport The export to check
     * @returns true if the export can be cancelled
     */
    canCancel(dataExport: AdminDataExport): boolean {
        return dataExport.dataExportState === DataExportState.REQUESTED || dataExport.dataExportState === DataExportState.IN_CREATION;
    }

    /**
     * Cancels a pending data export.
     * Called by the delete button directive after user confirms in the dialog.
     *
     * @param dataExport The export to cancel
     */
    cancelExport(dataExport: AdminDataExport): void {
        this.adminDataExportsService.cancelDataExport(dataExport.id).subscribe({
            next: () => {
                this.alertService.success('artemisApp.dataExport.admin.cancelSuccess', { login: dataExport.userLogin ?? '' });
                this.loadDataExports();
            },
            error: () => {
                this.alertService.error('artemisApp.dataExport.admin.cancelError', { login: dataExport.userLogin ?? '' });
            },
        });
    }
}
