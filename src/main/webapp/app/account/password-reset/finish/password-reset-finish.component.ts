import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PasswordResetFinishService } from './password-reset-finish.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from 'app/app.constants';

@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    @ViewChild('newPassword', { static: false })
    newPassword?: ElementRef;

    readonly PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    readonly PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;

    initialized = false;
    doNotMatch = false;
    error = false;
    success = false;
    key = '';

    passwordForm = this.fb.nonNullable.group({
        newPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
        confirmPassword: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH), Validators.maxLength(PASSWORD_MAX_LENGTH)]],
    });

    constructor(private passwordResetFinishService: PasswordResetFinishService, private route: ActivatedRoute, private profileService: ProfileService, private fb: FormBuilder) {}

    ngOnInit() {
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
            this.passwordResetFinishService.save(this.key, newPassword).subscribe({
                next: () => (this.success = true),
                error: () => (this.error = true),
            });
        }
    }
}
