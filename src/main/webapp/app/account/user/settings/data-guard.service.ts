import { Service, inject } from '@angular/core';
import { CanActivate } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA, MODULE_FEATURE_HYPERION, MODULE_FEATURE_IRIS, MODULE_FEATURE_METIS } from 'app/app.constants';

/**
 * Guard to check if the route "/user-settings/ai-experience" can be activated.
 */
@Service()
export class DataGuard implements CanActivate {
    private readonly profileService = inject(ProfileService);

    /**
     * Check if the client can activate a route.
     *
     * @return true if {@link isUsingLLM} returns true, false otherwise.
     */
    canActivate(): boolean | Promise<boolean> {
        return this.isUsingLLM();
    }

    /**
     * Whether a module that sends data to a language model is active, so that users can record their AI decision.
     * Metis counts as well: when it is the only such module, students still have to be able to decide.
     */
    isUsingLLM(): boolean {
        const isIrisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
        const isAthenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);
        const isHyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
        const isMetisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_METIS);

        return isIrisEnabled || isAthenaEnabled || isHyperionEnabled || isMetisEnabled;
    }
}
