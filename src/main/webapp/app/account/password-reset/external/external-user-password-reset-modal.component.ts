import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-external-user-password-reset-modal',
    templateUrl: './external-user-password-reset-modal.component.html',
})
export class ExternalUserPasswordResetModalComponent {
    externalCredentialProvider: string;
    externalPasswordResetLink: string;

    constructor(private activeModal: NgbActiveModal) {}

    /**
     * Closes the dialog, removes the query parameter and shows a helper message
     */
    clear(): void {
        this.activeModal.dismiss();
    }
}
