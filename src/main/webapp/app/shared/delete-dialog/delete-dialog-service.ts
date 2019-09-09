import { Injectable } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
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
    constructor(private modalService: NgbModal) {}

    /**
     * Opens delete dialog and returns a result after dialog is closed
     * @param deleteDialogData data that is used in dialog
     */
    openDeleteDialog(deleteDialogData: DeleteDialogData): Observable<any> {
        const modalRef = this.modalService.open(DeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.entityTitle = deleteDialogData.entityTitle;
        modalRef.componentInstance.deleteQuestion = deleteDialogData.deleteQuestion;
        modalRef.componentInstance.deleteConfirmationText = deleteDialogData.deleteConfirmationText;
        return from(modalRef.result);
    }
}
