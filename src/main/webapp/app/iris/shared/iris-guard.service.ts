import { Service, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';

@Service()
export class IrisGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if Iris is enabled for this instance, false otherwise
     */
    canActivate(): boolean {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS)) {
            void this.router.navigate(['/']);
            return false;
        }
        return true;
    }
}
