import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-unsaved-changes-warning',
    templateUrl: './privacy-statement-unsaved-changes-warning.component.html',
    styleUrls: ['./privacy-statement-unsaved-changes-warning.component.scss'],
})
export class PrivacyStatementUnsavedChangesWarningComponent {
    constructor(private activeModal: NgbActiveModal) {}

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
