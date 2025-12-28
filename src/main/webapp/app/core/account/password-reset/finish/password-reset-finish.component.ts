import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PasswordStrengthBarComponent } from 'app/core/account/password/password-strength-bar.component';

import { PasswordResetFinishService } from './password-reset-finish.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    imports: [TranslateDirective, RouterLink, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    private readonly passwordResetFinishService = inject(PasswordResetFinishService);
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
    finishReset(): void {
        // Reset error states before attempting reset
        this.doNotMatch.set(false);
        this.error.set(false);

        const { newPassword, confirmPassword } = this.passwordForm.controls;

        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
        } else {
            this.passwordResetFinishService.completePasswordReset(this.resetKey(), newPassword.value).subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
        }
    }
}
