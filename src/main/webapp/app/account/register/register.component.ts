import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RegisterService } from 'app/account/register/register.service';
import { User } from 'app/core/user/user.model';
import { ACCOUNT_REGISTRATION_BLOCKED, EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PasswordStrengthBarComponent } from '../password/password-strength-bar.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-register',
    templateUrl: './register.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisSharedCommonModule, ArtemisSharedModule],
})
export class RegisterComponent implements OnInit, AfterViewInit {
    private translateService = inject(TranslateService);
    private registerService = inject(RegisterService);
    private fb = inject(FormBuilder);
    private profileService = inject(ProfileService);

    @ViewChild('login', { static: false })
    login?: ElementRef;

    readonly USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    readonly USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    doNotMatch = false;
    error = false;
    errorEmailExists = false;
    errorUserExists = false;
    errorAccountRegistrationBlocked = false;
    success = false;

    usernamePattern = '^[a-zA-Z0-9]*';

    registerForm: FormGroup;
    isRegistrationEnabled = false;
    allowedEmailPattern?: string;
    allowedEmailPatternReadable?: string;

    ngAfterViewInit(): void {
        if (this.login) {
            this.login.nativeElement.focus();
        }
    }

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
                this.allowedEmailPattern = profileInfo.allowedEmailPattern;
                this.allowedEmailPatternReadable = profileInfo.allowedEmailPatternReadable;
                if (this.allowedEmailPattern) {
                    const jsRegexPattern = this.allowedEmailPattern;
                    this.registerForm.get('email')!.setValidators([Validators.required, Validators.minLength(4), Validators.maxLength(100), Validators.pattern(jsRegexPattern)]);
                }
            }
        });
        this.initializeForm();
    }

    private initializeForm() {
        if (this.registerForm) {
            return;
        }
        this.registerForm = this.fb.nonNullable.group({
            firstName: ['', [Validators.required, Validators.minLength(2)]],
            lastName: ['', [Validators.required, Validators.minLength(2)]],
            login: ['', [Validators.required, Validators.minLength(USERNAME_MIN_LENGTH), Validators.maxLength(USERNAME_MAX_LENGTH), Validators.pattern(this.usernamePattern)]],
            email: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email]],
            password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
            confirmPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
        });
    }

    /**
     * Registers a new user in Artemis. This is only possible if the passwords match and there is no user with the same
     * e-mail or username. For the language the current browser language is selected.
     */
    register(): void {
        this.doNotMatch = false;
        this.error = false;
        this.errorEmailExists = false;
        this.errorUserExists = false;

        const password = this.registerForm.get(['password'])!.value;
        if (password !== this.registerForm.get(['confirmPassword'])!.value) {
            this.doNotMatch = true;
        } else {
            const user = new User();
            user.firstName = this.registerForm.get(['firstName'])!.value;
            user.lastName = this.registerForm.get(['lastName'])!.value;
            user.login = this.registerForm.get(['login'])!.value;
            user.email = this.registerForm.get(['email'])!.value;
            user.password = password;
            user.langKey = this.translateService.currentLang;
            this.registerService.save(user).subscribe({
                next: () => (this.success = true),
                error: (response) => this.processError(response),
            });
        }
    }

    private processError(response: HttpErrorResponse): void {
        if (response.status === 400 && response.error.type.includes(LOGIN_ALREADY_USED_TYPE)) {
            this.errorUserExists = true;
        } else if (response.status === 400 && response.error.type.includes(EMAIL_ALREADY_USED_TYPE)) {
            this.errorEmailExists = true;
        } else if (response.status === 400 && response.error.type.includes(ACCOUNT_REGISTRATION_BLOCKED)) {
            this.errorAccountRegistrationBlocked = true;
        } else {
            this.error = true;
        }
    }
}
