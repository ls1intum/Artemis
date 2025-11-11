import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faKey, faLock, faShieldAlt } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-passkey-required',
    imports: [TranslateDirective, FaIconComponent, ButtonComponent],
    templateUrl: './passkey-required.component.html',
    styleUrl: './passkey-required.component.scss',
})
export class PasskeyRequiredComponent implements OnInit {
    protected readonly ButtonType = ButtonType;
    protected readonly faLock = faLock;
    protected readonly faKey = faKey;
    protected readonly faShieldAlt = faShieldAlt;

    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly accountService = inject(AccountService);
    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);

    userHasRegisteredAPasskey: boolean = false;
    returnUrl: string | null = null;

    ngOnInit() {
        this.userHasRegisteredAPasskey = !this.accountService.userIdentity()?.askToSetupPasskey;

        // Get the return URL from query parameters
        this.route.queryParams.subscribe((params) => {
            this.returnUrl = params['returnUrl'] || '/';
        });

        // If user is already logged in with passkey, redirect immediately
        if (this.accountService.isLoggedInWithPasskey() && this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
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

    private handleLoginSuccess() {
        // Update the user identity to reflect passkey login
        this.accountService.userIdentity.set({
            ...this.accountService.userIdentity(),
            isLoggedInWithPasskey: true,
            internal: this.accountService.userIdentity()?.internal ?? false,
        });

        // Redirect to the original URL or home
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            this.router.navigate(['/']);
        }
    }
}
