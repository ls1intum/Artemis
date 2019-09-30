import { DeleteDialogData, DeleteDialogService } from 'app/shared/delete-dialog/delete-dialog.service';
import { Output, EventEmitter, Input, Directive, HostListener } from '@angular/core';

@Directive({ selector: '[jhiDeleteDialog]' })
export class DeleteDialogDirective {
    @Input() entityTitle: string;
    @Input() deleteQuestion: string;
    @Input() deleteConfirmationText: string;
    @Input() checkboxText: string;
    @Input() additionalCheckboxText: string;
    @Output() delete = new EventEmitter<any>();

    constructor(private deleteDialogService: DeleteDialogService) {}

    /**
     * Opens delete dialog
     */
    openDeleteDialog() {
        const deleteDialogData: DeleteDialogData = {
            entityTitle: this.entityTitle,
            deleteQuestion: this.deleteQuestion,
            deleteConfirmationText: this.deleteConfirmationText,
            checkboxText: this.checkboxText,
            additionalCheckboxText: this.additionalCheckboxText,
        };
        this.deleteDialogService.openDeleteDialog(deleteDialogData).subscribe(result => {
            console.log(result);
            this.delete.emit(result);
        });
    }

    @HostListener('click')
    onClick() {
        this.openDeleteDialog();
    }
}
