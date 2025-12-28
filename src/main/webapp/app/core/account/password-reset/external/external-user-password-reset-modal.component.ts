import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Modal dialog shown to external/SSO users who attempt to reset their password.
 * Informs them that password reset must be done through their external identity provider
 * (e.g., university SSO, LDAP) and provides a link to the external reset page if available.
 */
@Component({
    selector: 'jhi-external-user-password-reset-modal',
    templateUrl: './external-user-password-reset-modal.component.html',
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExternalUserPasswordResetModalComponent {
    private activeModal = inject(NgbActiveModal);

    /**
     * Name of the external credential provider (e.g., "TUM SSO", "University LDAP").
     * Set via NgbModal.componentInstance when opening the modal.
     */
    externalCredentialProvider: string;

    /**
     * URL to the external password reset page, localized to user's language.
     * Set via NgbModal.componentInstance when opening the modal.
     */
    externalPasswordResetLink: string;

    /**
     * Dismisses the modal dialog without any action.
     */
    dismiss(): void {
        this.activeModal.dismiss();
    }
}
