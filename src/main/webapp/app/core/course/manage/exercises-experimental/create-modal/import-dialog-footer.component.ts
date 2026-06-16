import { Component, inject } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';

/** Sentinel the import dialog closes with when the user presses "Back", so the caller can reopen the manage-exercises modal. */
export const IMPORT_DIALOG_BACK = '__import_dialog_back__';

/**
 * Footer rendered inside the regular Artemis import dialog when it is opened from the experimental manage-exercises
 * modal. It adds a "Back" button that closes the dialog with the {@link IMPORT_DIALOG_BACK} sentinel so the caller
 * can reopen the manage-exercises modal at the exercise-type selection step.
 */
@Component({
    selector: 'jhi-import-dialog-footer',
    // Full-width flex pushes the button to the left edge of the dialog footer (PrimeNG right-aligns footers by default).
    template: `<div class="d-flex justify-content-start w-100">
        <p-button severity="secondary" [outlined]="true" size="small" (onClick)="back()"> <fa-icon [icon]="faArrowLeft" class="me-1" />Back </p-button>
    </div>`,
    styles: [':host { width: 100%; }'],
    imports: [ButtonModule, FaIconComponent],
})
export class ImportDialogFooterComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    protected readonly faArrowLeft = faArrowLeft;

    back(): void {
        this.dialogRef.close(IMPORT_DIALOG_BACK);
    }
}
