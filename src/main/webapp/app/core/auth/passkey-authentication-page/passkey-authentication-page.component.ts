import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { faArrowUpRightFromSquare, faKey, faLock } from '@fortawesome/free-solid-svg-icons';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-passkey-required',
    imports: [TranslateDirective, FaIconComponent, ButtonComponent, RouterLink],
    templateUrl: './passkey-authentication-page.component.html',
})
export class PasskeyAuthenticationPageComponent implements OnInit, OnDestroy {
    protected readonly faKey = faKey;
    protected readonly faLock = faLock;
    protected readonly faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly webauthnService = inject(WebauthnService);
    private readonly alertService = inject(AlertService);
    protected readonly accountService = inject(AccountService);

    returnUrl: string | undefined = undefined;

    private routeSubscription?: Subscription;

    ngOnInit() {
        this.initializeUserIdentity().then(() => {
            const redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey = this.accountService.isUserLoggedInWithApprovedPasskey() && this.returnUrl;
            if (redirectDirectlyIfUserIsAlreadyLoggedInWithPasskey) {
                this.router.navigateByUrl(this.returnUrl!);
            }
        });

        this.routeSubscription = this.route.queryParams.subscribe((params) => {
            this.returnUrl = params['returnUrl'] || '/';
        });
    }

    ngOnDestroy() {
        this.routeSubscription?.unsubscribe();
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
            this.redirectToOriginalUrlOrHome();
        } else {
            this.alertService.error('global.menu.admin.usedPasskeyIsNotSuperAdminApproved');
        }
    }

    redirectToOriginalUrlOrHome() {
        if (this.returnUrl) {
            this.router.navigateByUrl(this.returnUrl);
        } else {
            this.router.navigate(['/']);
        }
    }
}
