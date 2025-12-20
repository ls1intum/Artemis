import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, viewChild } from '@angular/core';

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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetInitComponent implements AfterViewInit {
    private readonly passwordResetInitService = inject(PasswordResetInitService);
    private readonly profileService = inject(ProfileService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly modalService = inject(NgbModal);

    readonly emailUsernameElement = viewChild<ElementRef>('emailUsername');

    emailUsernameValue = '';
    externalResetModalRef: NgbModalRef | undefined;

    readonly useExternal: boolean;
    readonly externalCredentialProvider: string;
    readonly externalPasswordResetLink?: string;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.useExternal = profileInfo.useExternal;
        this.externalCredentialProvider = profileInfo.externalCredentialProvider;
        const lang = this.translateService.getCurrentLang();
        this.externalPasswordResetLink = profileInfo.externalPasswordResetLinkMap?.[lang] ?? profileInfo.externalPasswordResetLinkMap?.['en'];
    }

    ngAfterViewInit(): void {
        this.emailUsernameElement()?.nativeElement.focus();
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
