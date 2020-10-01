import { AfterViewInit, Component, ElementRef, OnInit, Renderer2 } from '@angular/core';
import { PasswordResetInitService } from './password-reset-init.service';
import { EMAIL_NOT_FOUND_TYPE } from 'app/shared/constants/error.constants';
import { Account } from 'app/core/user/account.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-password-reset-init',
    templateUrl: './password-reset-init.component.html',
})
export class PasswordResetInitComponent implements OnInit, AfterViewInit {
    error: string | null;
    errorEmailNotExists: string | null;
    resetAccount: Account;
    success: string | null;
    isRegistrationEnabled = false;

    constructor(private passwordResetInitService: PasswordResetInitService, private elementRef: ElementRef, private renderer: Renderer2, private profileService: ProfileService) {}

    ngOnInit() {
        this.resetAccount = new Account();
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled;
            }
        });
    }

    ngAfterViewInit() {
        const emailElement = this.elementRef.nativeElement.querySelector('#email');
        if (emailElement != null) {
            this.renderer.selectRootElement(emailElement, true).focus();
        }
    }

    requestReset() {
        this.error = null;
        this.errorEmailNotExists = null;

        this.passwordResetInitService.save(this.resetAccount.email!).subscribe(
            () => {
                this.success = 'OK';
            },
            (response) => {
                this.success = null;
                if (response.status === 400 && response.error.type === EMAIL_NOT_FOUND_TYPE) {
                    this.errorEmailNotExists = 'ERROR';
                } else {
                    this.error = 'ERROR';
                }
            },
        );
    }
}
