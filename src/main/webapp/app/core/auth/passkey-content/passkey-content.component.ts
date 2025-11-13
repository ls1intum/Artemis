import { Component, OnInit, inject, input, output } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
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

    protected readonly ButtonType = ButtonType;

    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    protected readonly accountService = inject(AccountService);

    showHeader = input<boolean>(true);
    showContent = input<boolean>(true);
    showFooter = input<boolean>(true);

    triggerPasskeyLoginSuccessHandler = output<void>();

    userHasRegisteredAPasskey: boolean = false;

    ngOnInit() {
        this.initializeUserIdentity().then((response) => {});
    }

    /**
     * Ensures the user identity is loaded from the server (important for page reloads)
     */
    private async initializeUserIdentity() {
        await this.accountService.identity();

        this.userHasRegisteredAPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;
    }

    async setupPasskeyAndLogin() {
        // this.showModal = false;
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        await this.signInWithPasskey();
    }

    async signInWithPasskey() {
        await this.webauthnService.loginWithPasskey();
        await this.accountService.identity(true);
        this.triggerPasskeyLoginSuccessHandler.emit();
    }
}
