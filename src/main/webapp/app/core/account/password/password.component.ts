import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

import { PasswordService } from './password.service';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PasswordStrengthBarComponent } from './password-strength-bar.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

interface PasswordForm {
    currentPassword: FormControl<string>;
    newPassword: FormControl<string>;
    confirmPassword: FormControl<string>;
}

@Component({
    selector: 'jhi-password',
    templateUrl: './password.component.html',
    imports: [TranslateDirective, FormsModule, ReactiveFormsModule, PasswordStrengthBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordComponent implements OnInit {
    private readonly passwordService = inject(PasswordService);
    private readonly accountService = inject(AccountService);

    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    readonly doNotMatch = signal(false);
    readonly error = signal(false);
    readonly success = signal(false);
    readonly user = signal<User | undefined>(undefined);
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

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.user.set(user);
            this.passwordResetEnabled.set(user?.internal || false);
        });
    }

    /**
     * Changes the current user's password. It will only try to change it if the values in both the new password field
     * and the confirmation of the new password are the same.
     */
    changePassword() {
        this.error.set(false);
        this.success.set(false);
        this.doNotMatch.set(false);

        const { newPassword, confirmPassword, currentPassword } = this.passwordForm.controls;
        if (newPassword.value !== confirmPassword.value) {
            this.doNotMatch.set(true);
        } else {
            this.passwordService.save(newPassword.value, currentPassword.value).subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
        }
    }
}
