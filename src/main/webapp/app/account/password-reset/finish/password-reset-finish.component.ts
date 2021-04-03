import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PasswordResetFinishService } from './password-reset-finish.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    @ViewChild('newPassword', { static: false })
    newPassword?: ElementRef;

    initialized = false;
    doNotMatch = false;
    error = false;
    success = false;
    key = '';

    passwordForm = this.fb.group({
        newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
        confirmPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    });

    passwordResetEnabled = false;

    constructor(private passwordResetFinishService: PasswordResetFinishService, private route: ActivatedRoute, private profileService: ProfileService, private fb: FormBuilder) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.passwordResetEnabled = profileInfo.registrationEnabled || false;
                this.passwordResetEnabled ||= profileInfo.saml2?.enablePassword || false;
            }
        });

        this.route.queryParams.subscribe((params) => {
            if (params['key']) {
                this.key = params['key'];
            }
            this.initialized = true;
        });
    }

    ngAfterViewInit(): void {
        if (this.newPassword) {
            this.newPassword.nativeElement.focus();
        }
    }

    finishReset(): void {
        this.doNotMatch = false;
        this.error = false;

        const newPassword = this.passwordForm.get(['newPassword'])!.value;
        const confirmPassword = this.passwordForm.get(['confirmPassword'])!.value;

        if (newPassword !== confirmPassword) {
            this.doNotMatch = true;
        } else {
            this.passwordResetFinishService.save(this.key, newPassword).subscribe(
                () => (this.success = true),
                () => (this.error = true),
            );
        }
    }
}
