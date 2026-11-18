import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';

@Service()
export class PasskeyAuthenticationGuard implements CanActivate {
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);

    /**
     * Prevents a flickering when directly accessing e.g. an admin route directly via URL (e.g. bookmark).
     */
    private async ensureUserIdentityLoaded(): Promise<void> {
        await this.accountService.identity();
        return;
    }

    private async isLoggedInWithApprovedPasskey(): Promise<boolean> {
        await this.ensureUserIdentityLoaded();
        return this.accountService.isUserLoggedInWithApprovedPasskey();
    }

    /**
     * Check if the client can activate a route.
     * @param route The activated route snapshot
     * @param state The router state snapshot
     * @return true if the user has logged in with a passkey (or if passkey requirement is disabled), otherwise a redirect to
     * the passkey-required page that carries the attempted URL as returnUrl
     */
    async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<true | UrlTree> {
        if (!this.accountService.isPasskeyRequiredForAdministratorFeatures()) {
            return true;
        }

        if (await this.isLoggedInWithApprovedPasskey()) {
            return true;
        }

        const attemptedUrl = state.url;
        return this.router.createUrlTree(['/passkey-required'], {
            queryParams: { returnUrl: attemptedUrl },
        });
    }
}
