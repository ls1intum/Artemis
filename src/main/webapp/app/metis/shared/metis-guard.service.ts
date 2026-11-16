import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_METIS } from 'app/app.constants';

/**
 * Keeps the Metis pages out of reach while the Metis module is not active on this instance; the counterpart of the
 * Iris guard for the comprehension check.
 */
@Service()
export class MetisGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if Metis is enabled for this instance, otherwise a redirect to the start page
     */
    canActivate(): true | UrlTree {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_METIS)) {
            return this.router.createUrlTree(['/']);
        }
        return true;
    }
}
