import { DeleteDialogData, DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Component, Output, EventEmitter, Input } from '@angular/core';

@Component({
    selector: 'jhi-delete-button',
    templateUrl: './delete-button.component.html',
})
export class DeleteButtonComponent {
    @Input() entityTitle: string;
    @Input() deleteQuestion: string;
    @Input() deleteConfirmationText: string;
    @Output() delete = new EventEmitter<void>();

    constructor(private deleteDialogService: DeleteDialogService) {}

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const deleteDialogData: DeleteDialogData = { entityTitle: this.entityTitle, deleteQuestion: this.deleteQuestion, deleteConfirmationText: this.deleteConfirmationText };
        this.deleteDialogService.openDeleteDialog(deleteDialogData).subscribe(() => this.delete.emit());
    }
}
