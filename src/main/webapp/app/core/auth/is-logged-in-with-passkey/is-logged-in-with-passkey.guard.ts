import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({
    providedIn: 'root',
})
export class IsLoggedInWithPasskeyGuard implements CanActivate {
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @param route The activated route snapshot
     * @param state The router state snapshot
     * @return true if the user has logged in with a passkey, false otherwise
     */
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
        if (this.accountService.isLoggedInWithPasskey()) {
            return true;
        }

        // Redirect to passkey-required page with the attempted URL as a query parameter
        this.router.navigate(['/passkey-required'], {
            queryParams: { returnUrl: state.url },
        });

        return false;
    }
}
