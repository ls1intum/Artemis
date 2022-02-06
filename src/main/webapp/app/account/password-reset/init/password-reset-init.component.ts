import { AfterViewInit, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { PasswordResetInitService } from './password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormBuilder } from '@angular/forms';
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
    @ViewChild('emailUsername', { static: false })
    emailUsernameElement?: ElementRef;
    emailUsernameValue = '';
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
        if (this.emailUsernameElement) {
            this.emailUsernameElement.nativeElement.focus();
        }
    }

    requestReset(): void {
        if (!this.emailUsernameValue) {
            this.alertService.error('reset.request.messages.info');
            return;
        }
        this.passwordResetInitService.save(this.emailUsernameValue).subscribe({
            next: () => {
                this.alertService.success('reset.request.messages.success');
            },
            error: (err: HttpErrorResponse) => {
                if (this.useExternal && err?.error?.errorKey === 'externalUser') {
                    this.externalResetModalRef = this.modalService.open(ExternalUserPasswordResetModalComponent, { size: 'lg', backdrop: 'static' });
                    this.externalResetModalRef.componentInstance.externalCredentialProvider = this.externalCredentialProvider;
                    this.externalResetModalRef.componentInstance.externalPasswordResetLink = this.externalPasswordResetLink;
                } else {
                    onError(this.alertService, err);
                }
            },
        });
    }
}
