import { Component, OnInit, inject, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';
import { Router } from '@angular/router';
import { addNewPasskey } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { AlertService } from 'app/shared/service/alert.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';

@Component({
    selector: 'jhi-login-with-passkey-modal',
    imports: [DialogModule, TranslateDirective, FaIconComponent, ButtonComponent],
    templateUrl: './login-with-passkey.modal.html',
})
export class LoginWithPasskeyModal implements OnInit {
    // TODO fix that modal appears after 1. login with passkey 2. logout 3. login with password (seems like a change detection issue)

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

    userHasRegisteredAPasskey: boolean = false;

    ngOnInit() {
        this.userHasRegisteredAPasskey = !this.accountService.userIdentity?.askToSetupPasskey;
    }

    async setupPasskeyAndLogin() {
        await addNewPasskey(this.accountService.userIdentity, this.webauthnApiService, this.alertService);
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        await this.signInWithPasskey();
    }

    async signInWithPasskey() {
        this.showModal = false;
        await this.webauthnService.loginWithPasskey();
        this.handleLoginSuccess();
    }

    cancel() {
        this.showModal = false;
    }

    private handleLoginSuccess() {
        // TODO this could be done in the loginWithPasskey method
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
