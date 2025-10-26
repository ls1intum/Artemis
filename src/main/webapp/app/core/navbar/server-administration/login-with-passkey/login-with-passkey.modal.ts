import { Component, inject, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, TranslateDirective, FaIconComponent, ButtonComponent],
    templateUrl: './login-with-passkey.modal.html',
})
export class LoginWithPasskeyModal {
    protected readonly ButtonType = ButtonType;

    protected readonly faKey = faKey;
    protected readonly faLock = faLock;

    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);
    private readonly accountService = inject(AccountService);
    private readonly webauthnService = inject(WebauthnService);
    private readonly webauthnApiService = inject(WebauthnApiService);
    private readonly alertService = inject(AlertService);

    isLoggedInWithPasskeyOutput = output<boolean>();
    justLoggedInWithPasskey = output<boolean>();

    showModal: boolean = false;

    authenticationError = false;

    async signInWithPasskey() {
        try {
            await this.loginWithPasskey();
        } catch (error) {
            this.showModal = false;
            throw error;
        }
        this.showModal = false;
    }

    cancel() {
        this.showModal = false;
    }

    async loginWithPasskey() {
        try {
            const authenticatorCredential = await this.webauthnService.getCredential();

            if (!authenticatorCredential || authenticatorCredential.type != 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);
            this.handleLoginSuccess();
        } catch (error) {
            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.login');
            }
            // eslint-disable-next-line no-undef
            console.error(error);
            throw error;
        }
    }

    /**
     * Handle a successful user login.
     */
    private handleLoginSuccess() {
        this.authenticationError = false;
        this.accountService.userIdentity = {
            ...this.accountService.userIdentity,
            isLoggedInWithPasskey: true,
            internal: this.accountService.userIdentity?.internal ?? false,
        };

        this.isLoggedInWithPasskeyOutput.emit(true); // TODO can be done via effect one other PR is merged
        this.justLoggedInWithPasskey.emit(true);

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }
}
