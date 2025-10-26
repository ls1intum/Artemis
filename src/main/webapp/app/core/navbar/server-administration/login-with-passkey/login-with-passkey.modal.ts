import { Component, inject, output } from '@angular/core';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
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

    isLoggedInWithPasskeyOutput = output<boolean>();
    justLoggedInWithPasskey = output<boolean>();

    showModal: boolean = false;

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
        this.webauthnService.loginWithPasskey();
        this.handleLoginSuccess();
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
