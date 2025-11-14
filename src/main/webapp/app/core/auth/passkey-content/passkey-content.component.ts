import { Component, OnInit, inject, input, output } from '@angular/core';
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
    templateUrl: './passkey-content.component.html',
})
export class PasskeyContentComponent implements OnInit {
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

    ngOnInit() {
        this.initializeUserIdentity().then((response) => {});
    }

    handleLinkToUserSettingsClick() {
        this.linkToUserSettingsWasClicked.emit();
    }

    /**
     * Ensures the user identity is loaded from the server (important for page reloads)
     */
    private async initializeUserIdentity() {
        await this.accountService.identity();
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
