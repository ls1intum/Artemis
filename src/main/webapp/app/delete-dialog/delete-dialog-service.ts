import { Injectable } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/delete-dialog/delete-dialog.component';

/**
 * Data that will be passed to the delete dialog component
 */
export class DeleteDialogData {
    entityTitle: string;
    deleteQuestion: string;
    deleteConfirmationText?: string;
}
@Injectable({ providedIn: 'root' })
export class DeleteDialogService {
    constructor(private modalService: NgbModal) {}

    /**
     * Opens delete dialog and returns a result after dialog is closed
     * @param deleteDialogData data that is used in dialog
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData): Promise<any> {
        const modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        return modalRef.result;
    }
}
