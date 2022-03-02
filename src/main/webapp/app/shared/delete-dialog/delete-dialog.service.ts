import { Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { from } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';

@Injectable({ providedIn: 'root' })
export class DeleteDialogService {
    modalRef: NgbModalRef | null;

    constructor(private modalService: NgbModal, public alertService: AlertService) {}

    /**
     * Opens delete dialog
     * @param deleteDialogData data that is used in dialog
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData): void {
        this.alertService.closeAll();
        this.modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        this.modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        this.modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        this.modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        this.modalRef.componentInstance.additionalChecks = deleteDialogData.additionalChecks;
        this.modalRef.componentInstance.actionType = deleteDialogData.actionType;
        this.modalRef.componentInstance.delete = deleteDialogData.delete;
        this.modalRef.componentInstance.dialogError = deleteDialogData.dialogError;
        this.modalRef.componentInstance.requireConfirmationOnlyForAdditionalChecks = deleteDialogData.requireConfirmationOnlyForAdditionalChecks;

        from(this.modalRef.result).subscribe(() => (this.modalRef = null));
    }
}
