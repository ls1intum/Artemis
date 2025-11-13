import { Component, OnInit, inject, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Router, RouterLink } from '@angular/router';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faArrowUpRightFromSquare, faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';

export interface PasskeyActionButton {
    onClick: () => void;
    titleKey: string;
    icon?: IconDefinition;
    btnType?: ButtonType;
}

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

    private readonly router = inject(Router);
    private readonly eventManager = inject(EventManager);
    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    protected readonly accountService = inject(AccountService);

    showHeader = input<boolean>(true);
    showContent = input<boolean>(true);
    showFooter = input<boolean>(true);

    userHasRegisteredAPasskey: boolean = false;

    ngOnInit() {
        this.userHasRegisteredAPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;
    }

    async setupPasskeyAndLogin() {
        // this.showModal = false;
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        await this.signInWithPasskey();
    }

    async signInWithPasskey() {
        // this.showModal = false;
        await this.webauthnService.loginWithPasskey();
        this.handleLoginSuccess();
    }

    private handleLoginSuccess() {
        // TODO this could be done in the loginWithPasskey method
        this.accountService.userIdentity.set({
            ...this.accountService.userIdentity(),
            isLoggedInWithPasskey: true,
            internal: this.accountService.userIdentity()?.internal ?? false,
        });

        // this.justLoggedInWithPasskey.emit(true);

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }
}
