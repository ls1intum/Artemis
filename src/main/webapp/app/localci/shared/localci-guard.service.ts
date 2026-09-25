import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

@Service()
export class LocalCIGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if the local CI profile is active, otherwise a redirect to the course overview
     */
    canActivate(): boolean | UrlTree {
        if (!this.profileService.isProfileActive(PROFILE_LOCALCI)) {
            return this.router.createUrlTree(['/courses']);
        }
        return true;
    }
}
