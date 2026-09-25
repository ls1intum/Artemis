import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { MODULE_FEATURE_AIWORKER, MODULE_FEATURE_HYPERION_EXERCISE_GENERATION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/** Keeps AI operations unavailable when both generation and worker coordination are off. */
@Service()
export class AiGenerationGuard implements CanActivate {
    private readonly profiles = inject(ProfileService);
    private readonly router = inject(Router);

    canActivate(): boolean | UrlTree {
        if (this.profiles.isModuleFeatureActive(MODULE_FEATURE_HYPERION_EXERCISE_GENERATION) || this.profiles.isModuleFeatureActive(MODULE_FEATURE_AIWORKER)) {
            return true;
        }
        return this.router.createUrlTree(['/']);
    }
}
