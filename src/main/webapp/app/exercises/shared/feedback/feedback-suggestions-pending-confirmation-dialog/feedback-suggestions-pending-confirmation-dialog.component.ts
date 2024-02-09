import { Component } from '@angular/core';
import { faBan, faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-feedback-suggestions-pending-confirmation-dialog',
    templateUrl: './feedback-suggestions-pending-confirmation-dialog.component.html',
})
export class FeedbackSuggestionsPendingConfirmationDialogComponent {
    // Icons
    faBan = faBan;
    faTimes = faTimes;

    constructor(private activeModal: NgbActiveModal) {}

    /**
     * Close the confirmation dialog
     */
    close(confirm: boolean): void {
        this.activeModal.close(confirm);
    }
}
