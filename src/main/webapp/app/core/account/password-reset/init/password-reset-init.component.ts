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

/**
 * Component for initiating the password reset process.
 * Users enter their email or username and receive a password reset link via email.
 * For external/SSO users, displays information about resetting through their identity provider.
 */
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

    /** Reference to the email/username input field for auto-focus */
    readonly emailUsernameElement = viewChild<ElementRef>('emailUsername');

    /** The email address or username entered by the user */
    emailUsernameValue = '';

    /** Reference to the external user modal, used to close it programmatically */
    externalResetModalRef: NgbModalRef | undefined;

    /** Whether external authentication (SSO) is enabled on this instance */
    readonly useExternal: boolean;
    /** Name of the external credential provider (e.g., "TUM", "university SSO") */
    readonly externalCredentialProvider: string;
    /** URL for external password reset, localized to user's language */
    readonly externalPasswordResetLink?: string;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.useExternal = profileInfo.useExternal;
        this.externalCredentialProvider = profileInfo.externalCredentialProvider;

        // Get password reset link in user's language, falling back to English
        const currentLanguage = this.translateService.getCurrentLang();
        this.externalPasswordResetLink = profileInfo.externalPasswordResetLinkMap?.[currentLanguage] ?? profileInfo.externalPasswordResetLinkMap?.['en'];
    }

    /**
     * Sets focus to the email/username input field when the view initializes.
     */
    ngAfterViewInit(): void {
        this.emailUsernameElement()?.nativeElement.focus();
    }

    /**
     * Initiates the password reset process by sending a reset link to the user's email.
     * Shows an error if no email/username is provided.
     * For external users, displays a modal with instructions for their identity provider.
     */
    requestReset(): void {
        if (!this.emailUsernameValue) {
            this.alertService.error('reset.request.messages.info');
            return;
        }

        this.passwordResetInitService.requestPasswordReset(this.emailUsernameValue).subscribe({
            next: () => {
                this.alertService.success('reset.request.messages.success');
            },
            error: (errorResponse: HttpErrorResponse) => {
                // External/SSO users cannot reset passwords through Artemis
                if (this.useExternal && errorResponse?.error?.errorKey === 'externalUser') {
                    this.externalResetModalRef = this.modalService.open(ExternalUserPasswordResetModalComponent, { size: 'lg', backdrop: 'static' });
                    this.externalResetModalRef.componentInstance.externalCredentialProvider = this.externalCredentialProvider;
                    this.externalResetModalRef.componentInstance.externalPasswordResetLink = this.externalPasswordResetLink;
                } else {
                    onError(this.alertService, errorResponse);
                }
            },
        });
    }
}
