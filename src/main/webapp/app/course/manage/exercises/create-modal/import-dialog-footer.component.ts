import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TumUiButtonComponent } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** Sentinel the import dialog closes with when the user presses "Back", so the caller can reopen the manage-exercises modal. */
export const IMPORT_DIALOG_BACK = '__import_dialog_back__';

/**
 * Footer rendered inside the regular Artemis import dialog when it is opened from the manage-exercises
 * modal. It adds a "Back" button that closes the dialog with the {@link IMPORT_DIALOG_BACK} sentinel so the caller
 * can reopen the manage-exercises modal at the exercise-type selection step.
 */
@Component({
    selector: 'jhi-import-dialog-footer',
    template: `<tum-ui-button severity="secondary" variant="outlined" size="small" (clicked)="back()">
        <fa-icon [icon]="faArrowLeft" class="me-1" /><span jhiTranslate="entity.action.back"></span>
    </tum-ui-button>`,
    // The host is the row itself, so no wrapper element is needed. `flex: 1` claims the free space of a flex parent —
    // which is what pushes the button to the left edge of a dialog footer, since those right-align by default.
    // Unlike a hardcoded `width: 100%` it imposes nothing on a non-flex parent, so the component stays reusable.
    styles: [':host { display: flex; justify-content: flex-start; flex: 1; }'],
    imports: [TumUiButtonComponent, FaIconComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportDialogFooterComponent {
    private readonly dialogRef = inject(DynamicDialogRef);
    protected readonly faArrowLeft = faArrowLeft;

    back(): void {
        this.dialogRef.close(IMPORT_DIALOG_BACK);
    }
}
