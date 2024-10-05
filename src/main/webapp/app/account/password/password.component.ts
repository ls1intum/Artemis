import { Component, OnInit, inject } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PasswordService } from './password.service';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PasswordStrengthBarComponent } from './password-strength-bar.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-password',
    templateUrl: './password.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisSharedCommonModule, ArtemisSharedModule],
})
export class PasswordComponent implements OnInit {
    private passwordService = inject(PasswordService);
    private accountService = inject(AccountService);
    private fb = inject(FormBuilder);

    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    doNotMatch = false;
    error = false;
    success = false;
    user?: User;
    passwordForm: FormGroup;
    passwordResetEnabled = false;

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.user = user;
            this.passwordResetEnabled = user?.internal || false;
        });
        this.initializeForm();
    }

    private initializeForm() {
        if (this.passwordForm) {
            return;
        }
        this.passwordForm = this.fb.nonNullable.group({
            currentPassword: ['', [Validators.required]],
            newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
            confirmPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
        });
    }

    /**
     * Changes the current user's password. It will only try to change it if the values in both the new password field
     * and the confirmation of the new password are the same.
     */
    changePassword() {
        this.error = false;
        this.success = false;
        this.doNotMatch = false;

        const newPassword = this.passwordForm.get(['newPassword'])!.value;
        if (newPassword !== this.passwordForm.get(['confirmPassword'])!.value) {
            this.doNotMatch = true;
        } else {
            this.passwordService.save(newPassword, this.passwordForm.get(['currentPassword'])!.value).subscribe({
                next: () => (this.success = true),
                error: () => (this.error = true),
            });
        }
    }
}
