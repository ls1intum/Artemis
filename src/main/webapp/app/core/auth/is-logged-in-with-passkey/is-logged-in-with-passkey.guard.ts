import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MODULE_FEATURE_PASSKEY, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Injectable({
    providedIn: 'root',
})
export class IsLoggedInWithPasskeyGuard implements CanActivate {
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
     * Check if the client can activate a route.
     * @param route The activated route snapshot
     * @param state The router state snapshot
     * @return true if the user has logged in with a passkey (or if passkey requirement is disabled), false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
        if (!this.shouldEnforcePasskeyForAdminFeatures()) {
            return true;
        }

        if (this.accountService.isUserLoggedInWithApprovedPasskey()) {
            return true;
        }

        // Redirect to passkey-required page with the attempted URL as a query parameter
        this.router.navigate(['/passkey-required'], {
            queryParams: { returnUrl: state.url },
        });

        return false;
    }
}
