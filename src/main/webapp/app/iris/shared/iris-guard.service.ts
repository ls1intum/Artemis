import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';

@Service()
export class IrisGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if Iris is enabled for this instance, otherwise a redirect to the start page
     */
    canActivate(): true | UrlTree {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS)) {
            return this.router.createUrlTree(['/']);
        }
        return true;
    }
}
