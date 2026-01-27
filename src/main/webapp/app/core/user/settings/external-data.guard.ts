import { Injectable, inject } from '@angular/core';
import { CanActivate } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA, PROFILE_IRIS } from 'app/app.constants';

/**
 * Guard to check if the route "/user-settings/external-data" can be activated.
 */
@Injectable({ providedIn: 'root' })
export class ExternalDataGuard implements CanActivate {
    private readonly profileService = inject(ProfileService);

    /**
     * Check if the client can activate a route.
     *
     * @return true if {@link isUsingExternalLLM} returns true, false otherwise.
     */
    canActivate(): boolean | Promise<boolean> {
        return this.isUsingExternalLLM();
    }

    isUsingExternalLLM(): boolean {
        const isIrisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
        const isAthenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);

        return isIrisEnabled || isAthenaEnabled;
    }
}
