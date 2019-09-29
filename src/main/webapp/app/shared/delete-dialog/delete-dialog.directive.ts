import { DeleteDialogData, DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Output, EventEmitter, Input, Directive, HostListener } from '@angular/core';

@Directive({ selector: '[jhiDeleteDialog]' })
export class DeleteDialogDirective {
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

    @HostListener('click')
    onClick() {
        this.openDeleteDialog();
    }
}
