import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';

@Service()
export class HyperionGuard implements CanActivate {
    private readonly profileService = inject(ProfileService);
    private readonly router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if Hyperion is enabled for this instance, otherwise a redirect to the start page
     */
    canActivate(): true | UrlTree {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION)) {
            return this.router.createUrlTree(['/']);
        }
        return true;
    }
}
