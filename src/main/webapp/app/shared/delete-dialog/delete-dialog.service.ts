import { Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { from } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

@Injectable({ providedIn: 'root' })
export class DeleteDialogService {
    modalRef: NgbModalRef | null;

    constructor(private modalService: NgbModal, public jhiAlertService: JhiAlertService) {}

    /**
     * Opens delete dialog
     * @param deleteDialogData data that is used in dialog
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData): void {
        this.jhiAlertService.clear();
        this.modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        this.modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        this.modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        this.modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        this.modalRef.componentInstance.additionalChecks = deleteDialogData.additionalChecks;
        this.modalRef.componentInstance.actionType = deleteDialogData.actionType;
        this.modalRef.componentInstance.delete = deleteDialogData.delete;
        this.modalRef.componentInstance.dialogError = deleteDialogData.dialogError;
        from(this.modalRef.result).subscribe(
            () => (this.modalRef = null),
            () => {},
        );
    }
}
