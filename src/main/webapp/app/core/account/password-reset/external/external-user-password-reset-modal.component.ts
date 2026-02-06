import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DialogModule } from 'primeng/dialog';

/**
 * Modal dialog shown to external/SSO users who attempt to reset their password.
 * Informs them that password reset must be done through their external identity provider
 * (e.g., university SSO, LDAP) and provides a link to the external reset page if available.
 */
@Component({
    selector: 'jhi-external-user-password-reset-modal',
    templateUrl: './external-user-password-reset-modal.component.html',
    imports: [TranslateDirective, DialogModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExternalUserPasswordResetModalComponent {
    readonly visible = model<boolean>(false);

    /**
     * Name of the external credential provider (e.g., "TUM SSO", "University LDAP").
     */
    readonly externalCredentialProvider = input<string>('');

    /**
     * URL to the external password reset page, localized to user's language.
     */
    readonly externalPasswordResetLink = input<string>('');

    /**
     * Dismisses the modal dialog without any action.
     */
    dismiss(): void {
        this.visible.set(false);
    }
}
