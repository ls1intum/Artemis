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

interface RegisterForm {
    firstName: FormControl<string>;
    lastName: FormControl<string>;
    login: FormControl<string>;
    email: FormControl<string>;
    password: FormControl<string>;
    confirmPassword: FormControl<string>;
}

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

    readonly login = viewChild<ElementRef>('login');

    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    readonly doNotMatch = signal(false);
    readonly error = signal(false);
    readonly errorEmailExists = signal(false);
    readonly errorUserExists = signal(false);
    readonly errorAccountRegistrationBlocked = signal(false);
    readonly success = signal(false);

    readonly usernamePattern = '^[a-zA-Z0-9]*';

    readonly registerForm: FormGroup<RegisterForm>;
    readonly isRegistrationEnabled: boolean;
    readonly allowedEmailPattern?: string;
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

    ngAfterViewInit(): void {
        this.login()?.nativeElement.focus();
    }

    /**
     * Registers a new user in Artemis. This is only possible if the passwords match and there is no user with the same
     * e-mail or username. For the language the current browser language is selected.
     */
    register(): void {
        this.doNotMatch.set(false);
        this.error.set(false);
        this.errorEmailExists.set(false);
        this.errorUserExists.set(false);
        this.errorAccountRegistrationBlocked.set(false);

        const { password, confirmPassword, firstName, lastName, login, email } = this.registerForm.controls;
        if (password.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
        } else {
            const user = new User();
            user.firstName = firstName.value;
            user.lastName = lastName.value;
            user.login = login.value;
            user.email = email.value;
            user.password = password.value;
            user.langKey = this.translateService.getCurrentLang();
            this.registerService.save(user).subscribe({
                next: () => this.success.set(true),
                error: (response) => this.processError(response),
            });
        }
    }

    private processError(response: HttpErrorResponse): void {
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
