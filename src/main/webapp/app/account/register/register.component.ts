import { AfterViewInit, Component, ElementRef, OnInit, Renderer2 } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiLanguageService } from 'ng-jhipster';

import { RegisterService } from 'app/account/register/register.service';
import { User } from 'app/core/user/user.model';
import { EMAIL_ALREADY_USED_TYPE, LOGIN_ALREADY_USED_TYPE } from 'app/shared/constants/error.constants';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-register',
    templateUrl: './register.component.html',
})
export class RegisterComponent implements OnInit, AfterViewInit {
    confirmPassword: string;
    doNotMatch?: string;
    error?: string;
    errorEmailExists?: string;
    errorUserExists?: string;
    registerAccount: User;
    success: boolean;
    modalRef: NgbModalRef;
    isRegistrationEnabled = false;
    allowedEmailPattern?: string;

    constructor(
        private languageService: JhiLanguageService,
        private registerService: RegisterService,
        private elementRef: ElementRef,
        private renderer: Renderer2,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        this.success = false;
        this.registerAccount = new User();
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled;
                this.allowedEmailPattern = profileInfo.allowedEmailPattern;
                // TODO: show the email pattern to the user
                // TODO: check that the user follows the email pattern
            }
        });
    }

    ngAfterViewInit() {
        this.renderer.selectRootElement('#login', true).focus();
    }

    /**
     * Registers a new user in Artemis. This is only possible if the passwords match and there is no user with the same
     * e-mail or username. For the language the current browser language is selected.
     */
    register() {
        if (this.registerAccount.password !== this.confirmPassword) {
            this.doNotMatch = 'ERROR';
        } else {
            this.doNotMatch = undefined;
            this.error = undefined;
            this.errorUserExists = undefined;
            this.errorEmailExists = undefined;
            this.languageService.getCurrent().then((key) => {
                this.registerAccount.langKey = key;
                this.registerService.save(this.registerAccount).subscribe(
                    () => {
                        this.success = true;
                    },
                    (response) => this.processError(response),
                );
            });
        }
    }

    private processError(response: HttpErrorResponse) {
        this.success = false;
        // TODO: handle server errors better
        if (response.status === 400 && response.error.type === LOGIN_ALREADY_USED_TYPE) {
            this.errorUserExists = 'ERROR';
        } else if (response.status === 400 && response.error.type === EMAIL_ALREADY_USED_TYPE) {
            this.errorEmailExists = 'ERROR';
        } else {
            this.error = 'ERROR';
        }
    }
}
