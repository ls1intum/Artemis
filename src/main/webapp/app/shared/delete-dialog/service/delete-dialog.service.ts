import { Injectable, inject, signal } from '@angular/core';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/component/delete-dialog.component';
import { DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

@Injectable({ providedIn: 'root' })
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
            data: {
                entityTitle: deleteDialogData.entityTitle,
                deleteQuestion: deleteDialogData.deleteQuestion,
                translateValues: { ...deleteDialogData.translateValues, title: deleteDialogData.entityTitle },
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
