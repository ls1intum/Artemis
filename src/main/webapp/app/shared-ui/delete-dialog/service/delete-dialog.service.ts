import { DestroyRef, Service, inject, signal } from '@angular/core';
import { TumAetUiDialogRef, TumAetUiDialogService } from '@tumaet/ui-angular';
import { DeleteDialogComponent } from 'app/shared-ui/delete-dialog/component/delete-dialog.component';
import { DeleteDialogData } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, take } from 'rxjs';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Service()
export class DeleteDialogService {
    private dialogService = inject(TumAetUiDialogService);
    private readonly destroyRef = inject(DestroyRef);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);

    readonly dialogRef = signal<TumAetUiDialogRef | undefined>(undefined);
    private errorSubscription?: Subscription;

    constructor() {
        this.destroyRef.onDestroy(() => {
            this.dialogRef()?.close();
            this.errorSubscription?.unsubscribe();
        });
    }

    /**
     * Opens delete dialog
     * @param deleteDialogData data that is used in dialog
     * @param animation retained for compatibility with callers; motion follows the design system
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData, _animation = true): void {
        this.alertService.closeAll();
        // Clean up any previous error subscription
        this.errorSubscription?.unsubscribe();
        this.errorSubscription = undefined;

        // Keep errors observable after confirmation closes, including synchronous results during rendering.
        if (deleteDialogData.dialogError) {
            const errorSubscription = deleteDialogData.dialogError.pipe(take(1)).subscribe((errorMessage: string) => {
                if (errorMessage !== '') {
                    this.alertService.error(errorMessage);
                }
                this.errorSubscription = undefined;
            });
            this.errorSubscription = errorSubscription.closed ? undefined : errorSubscription;
        }

        const ref = this.dialogService.open<DeleteDialogComponent, unknown, DeleteDialogData>(DeleteDialogComponent, {
            header: this.getDialogHeader(deleteDialogData.actionType),
            size: 'medium',
            closable: true,
            closeOnEscape: true,
            dismissableMask: false,
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

        ref?.onClose.subscribe(() => {
            if (this.dialogRef() === ref) {
                this.dialogRef.set(undefined);
            }
        });
    }

    private getDialogHeader(actionType?: string): string {
        const headerKeys: { [key: string]: string } = {
            delete: 'entity.delete.title',
            reset: 'entity.reset.title',
            cleanup: 'entity.cleanup.title',
            remove: 'entity.remove.title',
            unlink: 'entity.unlink.title',
            noButtonTextDelete: 'entity.noButtonTextDelete.title',
            'no-button-text-delete': 'entity.noButtonTextDelete.title',
            endNow: 'entity.endNow.title',
            'end-now': 'entity.endNow.title',
        };
        const key = headerKeys[actionType || 'delete'] || 'entity.delete.title';
        return this.translateService.instant(key);
    }
}
