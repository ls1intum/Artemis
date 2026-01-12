import { Component, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { AdminDataExportsService } from 'app/core/admin/admin-data-exports/admin-data-exports.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TypeAheadUserSearchFieldComponent } from 'app/core/legal/data-export/type-ahead-search-field/type-ahead-user-search-field.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Modal component for creating a new data export for any user.
 *
 * Features:
 * - User search using the existing TypeAheadUserSearchFieldComponent
 * - Option to schedule the export (processed at 4 AM) or execute immediately
 * - Loading state during submission
 * - Success/error feedback via alerts
 *
 * This component uses PrimeNG Dialog and is controlled via the open() method.
 * The parent component should use @ViewChild to get a reference and call open().
 */
@Component({
    selector: 'jhi-admin-data-export-create-modal',
    template: `
        <p-dialog
            [header]="'artemisApp.dataExport.admin.createExport' | artemisTranslate"
            [modal]="true"
            [visible]="visible()"
            (visibleChange)="visible.set($event)"
            [closable]="true"
            [style]="{ width: '500px' }"
            appendTo="body"
        >
            <div class="mb-3">
                <jhi-type-ahead-user-search-field [(loginOrName)]="selectedUserLogin" />
            </div>
            <div class="mb-3">
                <div class="form-check">
                    <input type="radio" class="form-check-input" id="executeScheduled" name="executeOption" [value]="false" [(ngModel)]="executeNow" />
                    <label class="form-check-label" for="executeScheduled" jhiTranslate="artemisApp.dataExport.admin.schedule"></label>
                </div>
                <small class="text-muted" jhiTranslate="artemisApp.dataExport.admin.scheduleDescription"></small>
            </div>
            <div class="mb-3">
                <div class="form-check">
                    <input type="radio" class="form-check-input" id="executeNow" name="executeOption" [value]="true" [(ngModel)]="executeNow" />
                    <label class="form-check-label" for="executeNow" jhiTranslate="artemisApp.dataExport.admin.executeNow"></label>
                </div>
                <small class="text-muted" jhiTranslate="artemisApp.dataExport.admin.executeNowDescription"></small>
            </div>
            <ng-template pTemplate="footer">
                <div class="d-flex justify-content-end gap-2">
                    <button pButton severity="secondary" (click)="cancel()" jhiTranslate="entity.action.cancel"></button>
                    <button pButton [disabled]="!selectedUserLogin() || isSubmitting()" (click)="submit()">
                        @if (isSubmitting()) {
                            <fa-icon [icon]="faSpinner" animation="spin" class="me-1" />
                        }
                        <span jhiTranslate="artemisApp.dataExport.admin.createExport"></span>
                    </button>
                </div>
            </ng-template>
        </p-dialog>
    `,
    imports: [DialogModule, ButtonModule, TranslateDirective, FormsModule, FaIconComponent, TypeAheadUserSearchFieldComponent, ArtemisTranslatePipe],
})
export class AdminDataExportCreateModalComponent {
    private readonly adminDataExportsService = inject(AdminDataExportsService);
    private readonly alertService = inject(AlertService);

    /** Controls the visibility of the dialog */
    readonly visible = signal(false);

    /** The login of the selected user (bound to user search component) */
    readonly selectedUserLogin = model('');

    /** Whether to execute the export immediately vs scheduling it */
    readonly executeNow = model(false);

    /** Signal indicating whether a submission is in progress */
    readonly isSubmitting = signal(false);

    /** Callback function to be called when export is created successfully */
    private onSuccess?: () => void;

    protected readonly faSpinner = faSpinner;

    /**
     * Opens the dialog for creating a new data export.
     *
     * @param onSuccess Optional callback to invoke when an export is successfully created
     */
    open(onSuccess?: () => void): void {
        this.onSuccess = onSuccess;
        this.selectedUserLogin.set('');
        this.executeNow.set(false);
        this.isSubmitting.set(false);
        this.visible.set(true);
    }

    /**
     * Cancels and closes the dialog without taking any action.
     */
    cancel(): void {
        this.visible.set(false);
    }

    /**
     * Submits the request to create a new data export.
     *
     * The export can either be:
     * - Scheduled: Will be processed during the next scheduled run (typically 4 AM)
     * - Immediate: Created synchronously, may take time for large data sets
     *
     * On success, closes the dialog, shows a success alert, and invokes the onSuccess callback.
     * On failure, shows an error alert and keeps the dialog open.
     */
    submit(): void {
        const login = this.selectedUserLogin();
        if (!login) {
            return;
        }

        this.isSubmitting.set(true);
        this.adminDataExportsService.requestDataExportForUser(login, this.executeNow()).subscribe({
            next: () => {
                if (this.executeNow()) {
                    this.alertService.success('artemisApp.dataExport.admin.createSuccessImmediate', { login });
                } else {
                    this.alertService.success('artemisApp.dataExport.admin.createSuccessScheduled', { login });
                }
                this.visible.set(false);
                this.onSuccess?.();
            },
            error: () => {
                this.alertService.error('artemisApp.dataExport.admin.createError', { login });
                this.isSubmitting.set(false);
            },
        });
    }
}
