import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RegisterService } from 'app/core/account/register/register.service';
import { User } from 'app/core/user/user.model';
import { ACCOUNT_REGISTRATION_BLOCKED, EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';

import { PasswordStrengthBarComponent } from '../password/password-strength-bar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Type definition for the user registration form controls.
 */
interface RegisterForm {
    firstName: FormControl<string>;
    lastName: FormControl<string>;
    login: FormControl<string>;
    email: FormControl<string>;
    password: FormControl<string>;
    confirmPassword: FormControl<string>;
}

/**
 * Component for new user self-registration.
 * Allows users to create an account by providing their personal information,
 * email, username, and password. Validates input and handles registration errors.
 * Only available when registration is enabled on the Artemis instance.
 */
@Component({
    selector: 'jhi-register',
    templateUrl: './register.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterComponent implements AfterViewInit {
    private readonly translateService = inject(TranslateService);
    private readonly registerService = inject(RegisterService);
    private readonly profileService = inject(ProfileService);

    /** Reference to the login/username input field for auto-focus */
    readonly loginInput = viewChild<ElementRef>('login');

    /** Validation constraints exposed for template use */
    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    /** Indicates password and confirmation do not match */
    readonly doNotMatch = signal(false);
    /** Indicates a generic error occurred during registration */
    readonly error = signal(false);
    /** Indicates the email address is already registered */
    readonly errorEmailExists = signal(false);
    /** Indicates the username is already taken */
    readonly errorUserExists = signal(false);
    /** Indicates registration is blocked (e.g., by admin restriction) */
    readonly errorAccountRegistrationBlocked = signal(false);
    /** Indicates registration completed successfully */
    readonly success = signal(false);

    /**
     * Pattern for valid usernames: alphanumeric characters only.
     * Anchored with ^ and $ to match entire string.
     */
    readonly usernamePattern = '^[a-zA-Z0-9]+$';

    readonly registerForm: FormGroup<RegisterForm>;
    /** Whether self-registration is enabled on this Artemis instance */
    readonly isRegistrationEnabled: boolean;
    /** Optional regex pattern restricting allowed email domains (e.g., university emails only) */
    readonly allowedEmailPattern?: string;
    /** Human-readable description of allowed email pattern for display in UI */
    readonly allowedEmailPatternReadable?: string;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
        this.allowedEmailPattern = profileInfo.allowedEmailPattern;
        this.allowedEmailPatternReadable = profileInfo.allowedEmailPatternReadable;

        this.registerForm = new FormGroup<RegisterForm>({
            firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
            lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
            login: new FormControl('', {
                nonNullable: true,
                validators: [Validators.required, Validators.minLength(USERNAME_MIN_LENGTH), Validators.maxLength(USERNAME_MAX_LENGTH), Validators.pattern(this.usernamePattern)],
            }),
            email: new FormControl('', {
                nonNullable: true,
                // Use pattern validation if email domain is restricted, otherwise standard email validation
                validators: this.allowedEmailPattern
                    ? [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.pattern(this.allowedEmailPattern)]
                    : [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email],
            }),
            password: new FormControl('', {
                nonNullable: true,
                validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
            }),
            confirmPassword: new FormControl('', {
                nonNullable: true,
                validators: [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)],
            }),
        });
    }

    /**
     * Sets focus to the username input field when the view initializes.
     */
    ngAfterViewInit(): void {
        this.loginInput()?.nativeElement.focus();
    }

    /**
     * Attempts to register a new user account.
     * Validates that passwords match before submitting to the server.
     * The user's browser language is used as their preferred language.
     * Resets all error states before attempting registration.
     */
    register(): void {
        // Reset all error states
        this.doNotMatch.set(false);
        this.error.set(false);
        this.errorEmailExists.set(false);
        this.errorUserExists.set(false);
        this.errorAccountRegistrationBlocked.set(false);

        const { password, confirmPassword, firstName, lastName, login, email } = this.registerForm.controls;

        if (password.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
            return;
        }

        // Build user object from form values
        const newUser = new User();
        newUser.firstName = firstName.value;
        newUser.lastName = lastName.value;
        newUser.login = login.value;
        newUser.email = email.value;
        newUser.password = password.value;
        newUser.langKey = this.translateService.getCurrentLang();

        this.registerService.registerUser(newUser).subscribe({
            next: () => this.success.set(true),
            error: (response) => this.handleRegistrationError(response),
        });
    }

    /**
     * Handles registration errors by setting appropriate error signals.
     * Distinguishes between duplicate username, duplicate email, blocked registration,
     * and generic errors.
     *
     * @param response - The HTTP error response from the registration attempt
     */
    private handleRegistrationError(response: HttpErrorResponse): void {
        if (response.status === 400 && response.error.type.includes(LOGIN_ALREADY_USED_TYPE)) {
            this.errorUserExists.set(true);
        } else if (response.status === 400 && response.error.type.includes(EMAIL_ALREADY_USED_TYPE)) {
            this.errorEmailExists.set(true);
        } else if (response.status === 400 && response.error.type.includes(ACCOUNT_REGISTRATION_BLOCKED)) {
            this.errorAccountRegistrationBlocked.set(true);
        } else {
            this.error.set(true);
        }
    }
}
