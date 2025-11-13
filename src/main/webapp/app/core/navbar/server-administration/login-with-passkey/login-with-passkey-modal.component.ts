import { Component, inject, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';
import { Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { PasskeyContentComponent } from 'app/core/auth/passkey-content/passkey-content.component';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, ButtonComponent, PasskeyContentComponent],
    templateUrl: './login-with-passkey-modal.component.html',
})
export class LoginWithPasskeyModalComponent {
    protected readonly ButtonType = ButtonType;

    protected readonly faKey = faKey;
    protected readonly faLock = faLock;

    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);
    private readonly accountService = inject(AccountService);
    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    justLoggedInWithPasskey = output<boolean>();

    showModal: boolean = false;

    // TODO handle setup
    async setupPasskeyAndLogin() {
        this.showModal = false;
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
    }

    cancel() {
        this.showModal = false;
    }

    handleLoginSuccess() {
        this.justLoggedInWithPasskey.emit(true);
        this.showModal = false;

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }
}
