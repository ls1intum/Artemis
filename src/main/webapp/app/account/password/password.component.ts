import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { CredentialRevocationChoice, PasswordService } from './password.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PasswordStrengthBarComponent } from './password-strength-bar.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiFormFieldComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

/**
 * Type definition for the password change form controls.
 */
interface PasswordForm {
    currentPassword: FormControl<string>;
    newPassword: FormControl<string>;
    confirmPassword: FormControl<string>;
}

/**
 * Component that allows authenticated users to change their password.
 * Requires the current password for verification and validates that
 * the new password meets length requirements and matches confirmation.
 * Only available for internal users (not external/SSO users).
 */
@Component({
    selector: 'jhi-password',
    templateUrl: './password.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ReactiveFormsModule,
        PasswordStrengthBarComponent,
        ArtemisTranslatePipe,
        TumUiButtonComponent,
        TumUiCheckboxComponent,
        TumUiFormFieldComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordComponent implements OnInit {
    private readonly passwordService = inject(PasswordService);
    private readonly credentialRevocationConfirmationService = inject(CredentialRevocationConfirmationService);
    private readonly accountService = inject(AccountService);

    /** Minimum allowed password length exposed for template validation messages */
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    /** Maximum allowed password length exposed for template validation messages */
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    /** Indicates the new password and confirmation do not match */
    readonly doNotMatch = signal(false);
    /** Indicates an error occurred during password change */
    readonly error = signal(false);
    /** Indicates the password was successfully changed */
    readonly success = signal(false);
    /** The currently authenticated user */
    readonly user = signal<User | undefined>(undefined);
    /** Whether password reset is available (only for internal, non-SSO users) */
    readonly passwordResetEnabled = signal(false);

    /**
     * Whether the user indicated that the old password may have been leaked or stolen. Only they can answer that, and it
     * is what decides whether losing the credentials enrolled on their devices is warranted.
     */
    readonly passwordMayBeCompromised = signal(false);

    readonly revokePasskeys = signal(true);

    readonly revokeSshKeys = signal(true);

    readonly revokeVcsAccessTokens = signal(true);

    readonly passwordForm = new FormGroup<PasswordForm>({
        currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
        newPassword: new FormControl('', {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
        }),
        confirmPassword: new FormControl('', {
            nonNullable: true,
            validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
        }),
    });

    /**
     * Loads the current user and determines if password change is available.
     * Password change is only enabled for internal users (not SSO/external).
     */
    ngOnInit() {
        void this.accountService.identity().then((user) => {
            this.user.set(user);
            // Only internal users can change their password; external/SSO users must use their identity provider
            this.passwordResetEnabled.set(user?.internal || false);
        });
    }

    /**
     * Builds the revocation choice sent to the server. Nothing is revoked unless the user said the old password may have
     * been compromised, so a routine rotation never costs them their authenticators, keys or tokens.
     */
    private revocationChoice(): CredentialRevocationChoice | undefined {
        if (!this.passwordMayBeCompromised()) {
            return undefined;
        }
        return {
            passkeys: this.revokePasskeys(),
            sshKeys: this.revokeSshKeys(),
            vcsAccessTokens: this.revokeVcsAccessTokens(),
        };
    }

    /**
     * Toggles whether the old password is treated as possibly compromised.
     *
     * @param compromised what the user selected
     */
    onPasswordMayBeCompromisedChange(compromised: boolean): void {
        this.passwordMayBeCompromised.set(compromised);
        if (!compromised) {
            // Restore the safe defaults rather than leaving the previous selection behind. The three options are
            // rendered inside `@if (passwordMayBeCompromised())`, so a group deselected before closing the section
            // would otherwise still be deselected when it is reopened - silently keeping a credential the user
            // would reasonably expect to be revoked, having been shown these as selected by default.
            this.revokePasskeys.set(true);
            this.revokeSshKeys.set(true);
            this.revokeVcsAccessTokens.set(true);
        }
    }

    /**
     * Attempts to change the user's password after validation.
     * Validates that new password and confirmation match before submitting.
     * Resets all status signals before attempting the change.
     */
    async changePassword(): Promise<void> {
        // Reset status signals before attempting password change
        this.error.set(false);
        this.success.set(false);
        this.doNotMatch.set(false);

        const { newPassword, confirmPassword, currentPassword } = this.passwordForm.controls;

        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
            return;
        }

        // Deleting the authenticators and keys is irreversible, so it is confirmed first. A routine change that revokes
        // nothing is not interrupted: the service resolves immediately when the choice is empty.
        const revocationChoice = this.revocationChoice();
        if (!(await this.credentialRevocationConfirmationService.confirm(revocationChoice))) {
            return;
        }

        this.passwordService.changePassword(newPassword.value, currentPassword.value, revocationChoice).subscribe({
            next: () => this.success.set(true),
            error: () => this.error.set(true),
        });
    }
}
