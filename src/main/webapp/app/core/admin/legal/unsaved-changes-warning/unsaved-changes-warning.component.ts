import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

/**
 * Modal component that warns users about unsaved changes before discarding them.
 */
@Component({
    selector: 'jhi-unsaved-changes-warning',
    templateUrl: './unsaved-changes-warning.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, TranslateDirective, ButtonComponent],
})
export class UnsavedChangesWarningComponent {
    private readonly activeModal = inject(NgbActiveModal);

    /** The warning message to display (set by NgbModal via componentInstance) */
    textMessage?: string;

    /**
     * Closes the modal in which the warning is shown and discards the changes
     *
     */
    discardContent() {
        this.activeModal.close();
    }

    /**
     * Closes the modal in which the warning is shown
     */
    continueEditing() {
        this.activeModal.dismiss('cancel');
    }
}
