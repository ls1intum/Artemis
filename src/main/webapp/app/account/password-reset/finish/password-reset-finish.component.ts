import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PasswordStrengthBarComponent } from 'app/account/password/password-strength-bar.component';

import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { PasswordResetFinishService } from './password-reset-finish.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiFormFieldComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

/**
 * Type definition for the password reset completion form controls.
 */
interface PasswordResetForm {
    newPassword: FormControl<string>;
    confirmPassword: FormControl<string>;
}

/**
 * Component for completing the password reset process.
 * Users arrive here from the password reset email link containing a unique key.
 * They enter and confirm their new password to complete the reset.
 */
@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
    imports: [
        TranslateDirective,
        RouterLink,
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
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    private readonly passwordResetFinishService = inject(PasswordResetFinishService);
    private readonly credentialRevocationConfirmationService = inject(CredentialRevocationConfirmationService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    /** Reference to the new password input field for auto-focus */
    readonly newPasswordInput = viewChild<ElementRef>('newPassword');

    /** Minimum allowed password length exposed for template validation messages */
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    /** Maximum allowed password length exposed for template validation messages */
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    /** Indicates the component has finished extracting the reset key from URL */
    readonly initialized = signal(false);
    /** Indicates the new password and confirmation do not match */
    readonly doNotMatch = signal(false);
    /** Indicates an error occurred during password reset (e.g., expired or invalid key) */
    readonly error = signal(false);
    /** Indicates the password was successfully reset */
    readonly success = signal(false);
    /** The reset key extracted from the URL query parameters */
    readonly resetKey = signal('');

    // Default to revoking, so the safe outcome needs no thought and keeping a credential is the deliberate act.
    // A reset only proves the person controls the mailbox, which is a weaker claim to the account than knowing the
    // current password - but forgetting a password is not the same as losing it, so re-enrolling every authenticator
    // and key should not be forced on someone who simply forgot.
    /** Whether all passkeys should be deleted as part of the reset */
    readonly revokePasskeys = signal(true);
    /** Whether all SSH keys should be deleted as part of the reset */
    readonly revokeSshKeys = signal(true);
    /** Whether the personal, participation and repository VCS access tokens should be deleted as part of the reset */
    readonly revokeVcsAccessTokens = signal(true);

    readonly passwordForm = new FormGroup<PasswordResetForm>({
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
     * Extracts the password reset key from URL query parameters on component initialization.
     * The key is required to verify the reset request with the server.
     */
    ngOnInit() {
        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            if (params['key']) {
                this.resetKey.set(params['key']);
            }
            this.initialized.set(true);
        });
    }

    /**
     * Sets focus to the new password input field when the view initializes.
     */
    ngAfterViewInit(): void {
        this.newPasswordInput()?.nativeElement.focus();
    }

    /**
     * Completes the password reset process by submitting the new password.
     * Validates that passwords match before sending to server.
     * The reset key from the email link is sent along with the new password.
     */
    async finishReset(): Promise<void> {
        // Reset error states before attempting reset
        this.doNotMatch.set(false);
        this.error.set(false);

        const { newPassword, confirmPassword } = this.passwordForm.controls;

        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
            return;
        }

        const revocationChoice = {
            passkeys: this.revokePasskeys(),
            sshKeys: this.revokeSshKeys(),
            vcsAccessTokens: this.revokeVcsAccessTokens(),
        };
        // This page arrives with all three options selected, so completing the reset deletes every authenticator, key and
        // token unless the user deselected them. That is the easiest destructive action in the product to trigger by
        // accident, and it is irreversible, so it is confirmed first. Deselecting all three skips the dialog.
        if (!(await this.credentialRevocationConfirmationService.confirm(revocationChoice))) {
            return;
        }

        this.passwordResetFinishService.completePasswordReset(this.resetKey(), newPassword.value, revocationChoice).subscribe({
            next: () => this.success.set(true),
            error: () => this.error.set(true),
        });
    }
}
