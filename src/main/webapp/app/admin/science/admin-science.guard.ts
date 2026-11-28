import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

/**
 * Guards the "/admin/science" route on both conditions that hide the sidebar entry.
 *
 * Every endpoint the page calls lives in the atlas module and is {@code @Conditional(AtlasEnabled.class)}, and the
 * resource is additionally behind the Science feature toggle - so with either switched off the page renders a shell
 * whose every request fails. Checking only the module left the toggle case reaching exactly the dead page this guard
 * exists to prevent.
 *
 * @returns true if the page can work, otherwise a redirect to the administration root.
 */
export const adminScienceGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
    const profileService = inject(ProfileService);
    const featureToggleService = inject(FeatureToggleService);
    const router = inject(Router);
    return featureToggleService
        .getFeatureToggleActive(FeatureToggle.Science)
        .pipe(map((scienceActive) => (scienceActive && profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) || router.createUrlTree(['/admin'])));
};
