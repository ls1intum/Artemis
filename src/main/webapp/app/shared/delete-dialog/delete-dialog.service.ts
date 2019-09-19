import { Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { Observable, from } from 'rxjs';

/**
 * Data that will be passed to the delete dialog component
 */
export class DeleteDialogData {
    // title of the entity we want to delete
    entityTitle: string;

    // i18n key, that will be translated
    deleteQuestion: string;

    // i18n key, if undefined no safety check will take place (input name of the entity)
    deleteConfirmationText?: string;
}
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
        return from(this.modalRef.result.then(() => (this.modalRef = null)));
    }
}
