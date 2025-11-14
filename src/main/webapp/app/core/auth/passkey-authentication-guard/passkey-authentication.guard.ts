import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MODULE_FEATURE_PASSKEY, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Injectable({
    providedIn: 'root',
})
export class PasskeyAuthenticationGuard implements CanActivate {
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly profileService = inject(ProfileService);

    shouldEnforcePasskeyForAdminFeatures() {
        const isPasskeyDisabled = !this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY);
        if (isPasskeyDisabled) {
            return false;
        }

        // noinspection UnnecessaryLocalVariableJS: not inlined because the variable name improves readability
        const isPasskeyRequiredForAdminFeatures = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
        return isPasskeyRequiredForAdminFeatures;
    }

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
     * @return true if the user has logged in with a passkey (or if passkey requirement is disabled), false otherwise
     */
    async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
        if (!this.shouldEnforcePasskeyForAdminFeatures()) {
            return true;
        }

        if (await this.isLoggedInWithApprovedPasskey()) {
            return true;
        }

        const attemptedUrl = state.url;
        this.router.navigate(['/passkey-required'], {
            queryParams: { returnUrl: attemptedUrl },
        });

        return false;
    }
}
