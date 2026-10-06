import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map, take } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { learnerProfileAvailable } from 'app/account/user/settings/learner-profile/learner-profile-availability';

/**
 * Guards the "/user-settings/profile" route. Every section of the learner profile is served by a module that can be
 * disabled, and a disabled module means the section's requests answer 404 and the page reports the failures as errors.
 * See {@link learnerProfileAvailable} for which module backs which section.
 *
 * The sidebar already hides the tab when no section can load; this guard additionally prevents direct navigation (e.g.
 * via a bookmark or URL) from opening the empty page by redirecting back to the user-settings root.
 *
 * @returns true if at least one section of the learner profile can load, otherwise a redirect to the user-settings root
 */
export const learnerProfileGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
    const profileService = inject(ProfileService);
    const featureToggleService = inject(FeatureToggleService);
    const router = inject(Router);
    return featureToggleService.getFeatureToggleActive(FeatureToggle.Memiris).pipe(
        take(1),
        map((memirisEnabled) => (learnerProfileAvailable(profileService, memirisEnabled) ? true : router.createUrlTree(['/user-settings']))),
    );
};
