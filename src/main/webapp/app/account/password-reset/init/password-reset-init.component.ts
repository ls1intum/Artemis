import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { PasswordResetInitService } from './password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder, Validators } from '@angular/forms';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExternalUserPasswordResetModalComponent } from 'app/account/password-reset/external/external-user-password-reset-modal.component';

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
    useExternal: boolean;
    externalCredentialProvider: string;
    externalPasswordResetLink?: string;
    externalResetModalRef: NgbModalRef | undefined;

    constructor(
        private passwordResetInitService: PasswordResetInitService,
        private fb: FormBuilder,
        private profileService: ProfileService,
        private alertService: AlertService,
        private translateService: TranslateService,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.passwordResetEnabled = profileInfo.registrationEnabled || false;
                this.passwordResetEnabled ||= profileInfo.saml2?.enablePassword || false;
                this.useExternal = profileInfo.useExternal;
                this.externalCredentialProvider = profileInfo.externalCredentialProvider;
                const lang = this.translateService.currentLang;
                const linkMap = profileInfo.externalPasswordResetLinkMap;
                if (linkMap[lang]) {
                    this.externalPasswordResetLink = linkMap[lang];
                } else {
                    this.externalPasswordResetLink = linkMap['en'];
                }
            }
        });
    }

    ngAfterViewInit(): void {
        if (this.email) {
            this.email.nativeElement.focus();
        }
    }

    requestReset(): void {
        this.passwordResetInitService.save(this.resetRequestForm.get(['email'])!.value).subscribe(
            () => {
                this.alertService.success('reset.request.messages.success');
                this.success = true;
            },
            (err: HttpErrorResponse) => {
                if (this.useExternal && err?.error?.errorKey === 'externalUser') {
                    this.externalResetModalRef = this.modalService.open(ExternalUserPasswordResetModalComponent, { size: 'lg', backdrop: 'static' });
                    this.externalResetModalRef.componentInstance.ldapCredentialProvider = this.externalCredentialProvider;
                    this.externalResetModalRef.componentInstance.ldapPasswordResetLink = this.externalPasswordResetLink;
                } else {
                    onError(this.alertService, err);
                }
            },
        );
    }
}
