import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-passkey-required',
    imports: [TranslateDirective, FaIconComponent, ButtonComponent],
    templateUrl: './passkey-required.component.html',
})
export class PasskeyRequiredComponent implements OnInit {
    protected readonly ButtonType = ButtonType;
    protected readonly faLock = faLock;
    protected readonly faKey = faKey;

    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly accountService = inject(AccountService);
    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    userHasRegisteredAPasskey: boolean = false;
    returnUrl: string | undefined = undefined;

    // TODO handle the passkey is not yet super admin approved

    ngOnInit() {
        this.initializeUserIdentity().then((response) => {});

        this.route.queryParams.subscribe((params) => {
            this.returnUrl = params['returnUrl'] || '/';
        });
    }

    /**
     * Ensures the user identity is loaded from the server (important for page reloads)
     */
    private async initializeUserIdentity() {
        await this.accountService.identity();

        this.userHasRegisteredAPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;

        const redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey = this.accountService.isLoggedInWithPasskey() && this.returnUrl;
        if (redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey) {
            this.router.navigateByUrl(this.returnUrl!);
        }
    }

    async setupPasskeyAndLogin() {
        await this.webauthnService.addNewPasskey(this.accountService.userIdentity());
        this.alertService.success('artemisApp.userSettings.passkeySettingsPage.success.registration');
        await this.signInWithPasskey();
    }

    async signInWithPasskey() {
        try {
            await this.webauthnService.loginWithPasskey();
            this.handleLoginSuccess();
        } catch (error) {
            this.alertService.error('artemisApp.userSettings.passkeySettingsPage.error.login');
        }
    }

    cancel() {
        this.router.navigate(['/']);
    }

    private redirectToOriginalUrlOrHome() {
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            this.router.navigate(['/']);
        }
    }

    private handleLoginSuccess() {
        // Update the user identity to reflect passkey login
        this.accountService.userIdentity.set({
            ...this.accountService.userIdentity(),
            isLoggedInWithPasskey: true,
            internal: this.accountService.userIdentity()?.internal ?? false,
        });

        this.redirectToOriginalUrlOrHome();
    }
}
