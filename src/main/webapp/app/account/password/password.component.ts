import { Component, OnInit } from '@angular/core';

import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { PasswordService } from './password.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';
import { PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH } from 'app/app.constants';

@Component({
    selector: 'jhi-password',
    templateUrl: './password.component.html',
})
export class PasswordComponent implements OnInit {
    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    doNotMatch = false;
    error = false;
    success = false;
    user?: User;
    passwordForm = this.fb.group({
        currentPassword: ['', [Validators.required]],
        newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
        confirmPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
    });
    passwordResetEnabled = false;

    constructor(private passwordService: PasswordService, private accountService: AccountService, private profileService: ProfileService, private fb: FormBuilder) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.passwordResetEnabled = profileInfo.registrationEnabled || false;
                this.passwordResetEnabled ||= profileInfo.saml2?.enablePassword || false;
            }
        });

        this.accountService.identity().then((user) => {
            this.user = user;
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
            this.passwordService.save(newPassword, this.passwordForm.get(['currentPassword'])!.value).subscribe(
                () => (this.success = true),
                () => (this.error = true),
            );
        }
    }
}
