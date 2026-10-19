import { Service, inject, signal } from '@angular/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Added to PrimeNG's modal z-index (1100) for the confirmation dialog. A confirmation is often opened from inside a dialog that
 * lives in the CDK overlay container (a TUM AET UI dialog with a delete button), and that container sits at z-index 1200 in
 * `global.scss` so that calendars inside PrimeNG dialogs stay visible. With the default base the confirmation would open
 * underneath the dialog that asked for it and could not be confirmed. 300 puts it at 1402, above the container and below the alerts.
 * PrimeNG keeps one z-index stack for all its layers and ignores the base when the top entry is another modal, so a
 * confirmation opened from a PrimeNG dialog still stacks just above that dialog.
 */
export const CONFIRMATION_DIALOG_BASE_Z_INDEX = 300;

@Service()
export class DeleteDialogService {
    private dialogService = inject(DialogService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);

    readonly dialogRef = signal<DynamicDialogRef | undefined>(undefined);
    private errorSubscription?: Subscription;

    /**
     * Opens delete dialog
     * @param deleteDialogData data that is used in dialog
     * @param animation if true, the modal will fade in and out (not used in PrimeNG, kept for API compatibility)
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData, animation = true): void {
        this.alertService.closeAll();
        // Clean up any previous error subscription
        this.errorSubscription?.unsubscribe();

        const ref = this.dialogService.open(DeleteDialogComponent, {
            header: this.getDialogHeader(deleteDialogData.actionType),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
            baseZIndex: CONFIRMATION_DIALOG_BASE_Z_INDEX,
            data: {
                entityTitle: deleteDialogData.entityTitle,
                deleteQuestion: deleteDialogData.deleteQuestion,
                translateValues: cloneWith(deleteDialogData.translateValues, { title: deleteDialogData.entityTitle }),
                deleteConfirmationText: deleteDialogData.deleteConfirmationText,
                additionalChecks: deleteDialogData.additionalChecks,
                entitySummaryTitle: deleteDialogData.entitySummaryTitle,
                actionType: deleteDialogData.actionType,
                buttonType: deleteDialogData.buttonType,
                delete: deleteDialogData.delete,
                dialogError: deleteDialogData.dialogError,
                requireConfirmationOnlyForAdditionalChecks: deleteDialogData.requireConfirmationOnlyForAdditionalChecks,
                fetchEntitySummary: deleteDialogData.fetchEntitySummary,
                fetchCategorizedEntitySummary: deleteDialogData.fetchCategorizedEntitySummary,
            },
        });

        this.dialogRef.set(ref ?? undefined);

        // Subscribe to dialogError in the service (which stays alive) to handle errors
        // even after the dialog is closed. This ensures errors are displayed via AlertService.
        if (deleteDialogData.dialogError) {
            this.errorSubscription = deleteDialogData.dialogError.subscribe((errorMessage: string) => {
                if (errorMessage !== '') {
                    this.alertService.error(errorMessage);
                }
                // Clean up subscription after receiving a response (success or error)
                this.errorSubscription?.unsubscribe();
                this.errorSubscription = undefined;
            });
        }

        ref?.onClose.subscribe(() => {
            this.dialogRef.set(undefined);
        });
    }

    private getDialogHeader(actionType?: string): string {
        const headerKeys: { [key: string]: string } = {
            delete: 'entity.delete.title',
            reset: 'entity.reset.title',
            cleanup: 'entity.cleanup.title',
            remove: 'entity.remove.title',
            unlink: 'entity.unlink.title',
            'no-button-text-delete': 'entity.noButtonTextDelete.title',
            'end-now': 'entity.endNow.title',
        };
        const key = headerKeys[actionType || 'delete'] || 'entity.delete.title';
        return this.translateService.instant(key);
    }
}
