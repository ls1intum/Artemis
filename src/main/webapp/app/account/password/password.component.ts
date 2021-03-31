import { Component, OnInit } from '@angular/core';

import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { PasswordService } from './password.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-password',
    templateUrl: './password.component.html',
})
export class PasswordComponent implements OnInit {
    doNotMatch = false;
    error = false;
    success = false;
    user?: User;
    passwordForm = this.fb.group({
        currentPassword: ['', [Validators.required]],
        newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
        confirmPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    });
    passwortResetEnabled = false;

    constructor(private passwordService: PasswordService, private accountService: AccountService, private profileService: ProfileService, private fb: FormBuilder) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.passwortResetEnabled = profileInfo.registrationEnabled || false;
                this.passwortResetEnabled ||= profileInfo.saml2?.enablePassword || false;
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
