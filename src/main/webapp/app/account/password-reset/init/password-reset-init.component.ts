import { AfterViewInit, Component, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { PasswordResetInitService } from './password-reset-init.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FormsModule } from '@angular/forms';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExternalUserPasswordResetModalComponent } from 'app/account/password-reset/external/external-user-password-reset-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-password-reset-init',
    templateUrl: './password-reset-init.component.html',
    standalone: true,
    imports: [TranslateDirective, FormsModule, ArtemisSharedCommonModule, ArtemisSharedModule],
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
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.useExternal = profileInfo.useExternal;
                this.externalCredentialProvider = profileInfo.externalCredentialProvider;
                const lang = this.translateService.currentLang;
                const linkMap = profileInfo.externalPasswordResetLinkMap;
                if (linkMap.get(lang)) {
                    this.externalPasswordResetLink = linkMap.get(lang);
                } else {
                    this.externalPasswordResetLink = linkMap.get('en');
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
