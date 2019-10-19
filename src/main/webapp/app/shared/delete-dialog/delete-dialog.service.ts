import { Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { DeleteDialogData } from 'app/shared/delete-dialog/delete-dialog.model';
import { from, Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class DeleteDialogService {
    modalRef: NgbModalRef | null;

    constructor(private modalService: NgbModal) {}

    /**
     * Opens delete dialog and returns a result after dialog is closed
     * @param deleteDialogData data that is used in dialog
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData): Observable<any> {
        this.modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        this.modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        this.modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        this.modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        this.modalRef.componentInstance.additionalChecks = deleteDialogData.additionalChecks;
        this.modalRef.componentInstance.actionType = deleteDialogData.actionType;
        this.modalRef.componentInstance.delete = deleteDialogData.delete;
        return from(this.modalRef.result).pipe(finalize(() => (this.modalRef = null)));
    }

    closeDialog(): void {
        if (this.modalRef) {
            this.modalRef.componentInstance.close();
        }
    }

    showAlert(errorMessage: string): void {
        if (this.modalRef) {
            this.modalRef.componentInstance.showAlert(errorMessage);
        }
    }
}
