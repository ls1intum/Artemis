import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';

@Injectable({
    providedIn: 'root',
})
export class IsLoggedInWithPasskeyGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if the user has logged in with a passkey, false otherwise
     */
    canActivate(): boolean {
        // TODO
        if (!this.profileService.isProfileActive(PROFILE_IRIS)) {
            this.router.navigate(['/']);
            return false;
        }
        return true;
    }
}
