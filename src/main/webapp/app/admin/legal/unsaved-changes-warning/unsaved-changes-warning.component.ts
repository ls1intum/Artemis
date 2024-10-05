import { Component, Input, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-unsaved-changes-warning',
    templateUrl: './unsaved-changes-warning.component.html',
})
export class UnsavedChangesWarningComponent {
    private activeModal = inject(NgbActiveModal);

    @Input()
    textMessage: string;

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
