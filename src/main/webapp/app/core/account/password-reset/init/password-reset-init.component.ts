import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';

import { PasswordResetInitService } from './password-reset-init.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExternalUserPasswordResetModalComponent } from 'app/core/account/password-reset/external/external-user-password-reset-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-password-reset-init',
    templateUrl: './password-reset-init.component.html',
    imports: [TranslateDirective, FormsModule, ArtemisTranslatePipe],
})
export class PasswordResetInitComponent implements OnInit, AfterViewInit {
    private passwordResetInitService = inject(PasswordResetInitService);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private modalService = inject(NgbModal);

    @ViewChild('emailUsername', { static: false })
    emailUsernameElement?: ElementRef;
    emailUsernameValue = '';
    useExternal: boolean;
    externalCredentialProvider: string;
    externalPasswordResetLink?: string;
    externalResetModalRef: NgbModalRef | undefined;

    ngOnInit() {
        const profileInfo = this.profileService.getProfileInfo();
        this.useExternal = profileInfo.useExternal;
        this.externalCredentialProvider = profileInfo.externalCredentialProvider;
        const lang = this.translateService.getCurrentLang();
        this.externalPasswordResetLink = profileInfo.externalPasswordResetLinkMap?.[lang] ?? profileInfo.externalPasswordResetLinkMap?.['en'];
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
