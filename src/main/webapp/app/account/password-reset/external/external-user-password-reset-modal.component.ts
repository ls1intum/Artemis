import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-external-user-password-reset-modal',
    templateUrl: './external-user-password-reset-modal.component.html',
    standalone: true,
    imports: [TranslateDirective, ArtemisSharedModule],
})
export class ExternalUserPasswordResetModalComponent {
    private activeModal = inject(NgbActiveModal);

    externalCredentialProvider: string;
    externalPasswordResetLink: string;

    /**
     * Closes the dialog, removes the query parameter and shows a helper message
     */
    clear(): void {
        this.activeModal.dismiss();
    }
}
