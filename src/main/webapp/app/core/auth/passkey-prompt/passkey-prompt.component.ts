import { Component, inject, input, output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { RouterLink } from '@angular/router';
import { faArrowUpRightFromSquare, faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-passkey-content',
    standalone: true,
    imports: [TranslateDirective, FaIconComponent, ButtonComponent, RouterLink],
    templateUrl: './passkey-prompt.component.html',
})
export class PasskeyPromptComponent {
    protected readonly faKey = faKey;
    protected readonly faLock = faLock;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    protected readonly accountService = inject(AccountService);

    showHeader = input<boolean>(true);
    showContent = input<boolean>(true);
    showFooter = input<boolean>(true);

    triggerPasskeyLoginSuccessHandler = output<void>();
    linkToUserSettingsWasClicked = output<void>();

    handleLinkToUserSettingsClick() {
        this.linkToUserSettingsWasClicked.emit();
    }

    async setupPasskey() {
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
    }

    async signInWithPasskey() {
        await this.webauthnService.loginWithPasskey();
        await this.accountService.identity(true);

        if (this.accountService.isUserLoggedInWithApprovedPasskey()) {
            this.triggerPasskeyLoginSuccessHandler.emit();
        } else {
            this.alertService.error('global.menu.admin.usedPasskeyIsNotSuperAdminApproved');
        }
    }
}
