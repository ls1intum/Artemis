import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { PasswordResetInitService } from './password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';

@Component({
    selector: 'jhi-password-reset-init',
    templateUrl: './password-reset-init.component.html',
})
export class PasswordResetInitComponent implements OnInit, AfterViewInit {
    @ViewChild('email', { static: false })
    email?: ElementRef;

    success = false;
    resetRequestForm = this.fb.group({
        email: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(100), Validators.email]],
    });
    passwordResetEnabled = false;

    constructor(private passwordResetInitService: PasswordResetInitService, private fb: FormBuilder, private profileService: ProfileService) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.passwordResetEnabled = profileInfo.registrationEnabled || false;
                this.passwordResetEnabled ||= profileInfo.saml2?.enablePassword || false;
            }
        });
    }

    ngAfterViewInit(): void {
        if (this.email) {
            this.email.nativeElement.focus();
        }
    }

    requestReset(): void {
        this.passwordResetInitService.save(this.resetRequestForm.get(['email'])!.value).subscribe(() => (this.success = true));
    }
}
