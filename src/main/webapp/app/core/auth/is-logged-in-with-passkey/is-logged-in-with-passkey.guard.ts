import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({
    providedIn: 'root',
})
export class IsLoggedInWithPasskeyGuard implements CanActivate {
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if the user has logged in with a passkey, false otherwise
     */
    canActivate(): boolean {
        if (this.accountService.isLoggedInWithPasskey()) {
            return true;
        }

        this.router.navigate(['/']); // TODO redirect to a page that explains to login with passkey

        return false;
    }
}
