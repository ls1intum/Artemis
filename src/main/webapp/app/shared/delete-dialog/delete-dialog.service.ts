import { Injectable, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogData, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
import { Observable, from } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class DeleteDialogService {
    private modalService = inject(NgbModal);
    alertService = inject(AlertService);

    modalRef: NgbModalRef | null;

    /**
     * Opens delete dialog
     * @param deleteDialogData data that is used in dialog
     * @param animation if true, the modal will fade in and out
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData, animation = true): void {
        this.alertService.closeAll();
        this.modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static', animation });
        this.modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        this.modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        this.modalRef.componentInstance.translateValues = { ...deleteDialogData.translateValues, title: deleteDialogData.entityTitle };
        this.modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        this.modalRef.componentInstance.additionalChecks = deleteDialogData.additionalChecks;
        this.modalRef.componentInstance.entitySummaryTitle = deleteDialogData.entitySummaryTitle;
        this.modalRef.componentInstance.fetchEntitySummary = deleteDialogData.fetchEntitySummary;
        this.modalRef.componentInstance.actionType = deleteDialogData.actionType;
        this.modalRef.componentInstance.buttonType = deleteDialogData.buttonType;
        this.modalRef.componentInstance.delete = deleteDialogData.delete;
        this.modalRef.componentInstance.dialogError = deleteDialogData.dialogError;
        this.modalRef.componentInstance.requireConfirmationOnlyForAdditionalChecks = deleteDialogData.requireConfirmationOnlyForAdditionalChecks;

        if (deleteDialogData.fetchEntitySummary !== undefined) {
            this.fetchAndSetEntitySummary(deleteDialogData.fetchEntitySummary, this.modalRef.componentInstance);
        }

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }

    /**
     * Fetches and sets entity summary by subscribing to fetchEntitySummary.
     * @param fetchEntitySummary observable that fetches entity summary
     * @param componentInstance instance of DeleteDialogComponent
     */
    fetchAndSetEntitySummary(fetchEntitySummary: Observable<EntitySummary>, componentInstance: DeleteDialogComponent): void {
        fetchEntitySummary.subscribe({
            next: (entitySummary: EntitySummary) => (componentInstance.entitySummary = entitySummary),
            error: (error: HttpErrorResponse) => this.alertService.error('error.unexpectedError', { error: error.message }),
        });
    }
}
