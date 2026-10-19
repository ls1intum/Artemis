import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/**
 * Guards the "/user-settings/science" route. The science settings endpoint lives in the atlas module (the server-side
 * {@link https://github.com/ls1intum/Artemis ScienceSettingsResource} is annotated with {@code @Conditional(AtlasEnabled.class)}),
 * so when the atlas module is disabled the endpoint returns 404 and the page would render empty (issue #13173).
 *
 * The sidebar already hides the tab in that case; this guard additionally prevents direct navigation (e.g. via a
 * bookmark or URL) from opening the empty page by redirecting back to the user-settings root.
 *
 * @returns true if the atlas module is active, otherwise a redirect to the user-settings root.
 */
export const scienceSettingsGuard: CanActivateFn = (): boolean | UrlTree => {
    const profileService = inject(ProfileService);
    const router = inject(Router);
    if (profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
        return true;
    }
    return router.createUrlTree(['/user-settings']);
};
