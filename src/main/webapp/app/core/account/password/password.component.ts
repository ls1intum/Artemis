import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

import { PasswordService } from './password.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PasswordStrengthBarComponent } from './password-strength-bar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordComponent implements OnInit {
    private readonly passwordService = inject(PasswordService);
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
        this.accountService.identity().then((user) => {
            this.user.set(user);
            // Only internal users can change their password; external/SSO users must use their identity provider
            this.passwordResetEnabled.set(user?.internal || false);
        });
    }

    /**
     * Attempts to change the user's password after validation.
     * Validates that new password and confirmation match before submitting.
     * Resets all status signals before attempting the change.
     */
    changePassword() {
        // Reset status signals before attempting password change
        this.error.set(false);
        this.success.set(false);
        this.doNotMatch.set(false);

        const { newPassword, confirmPassword, currentPassword } = this.passwordForm.controls;

        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
        } else {
            this.passwordService.changePassword(newPassword.value, currentPassword.value).subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
        }
    }
}
