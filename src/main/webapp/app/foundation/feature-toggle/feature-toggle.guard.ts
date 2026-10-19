import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

/**
 * Guards a route behind a {@link FeatureToggle}. The feature-toggle directives already hide or disable the entry points
 * for a disabled feature, but they do not stop a direct navigation (a bookmark or a pasted URL) from loading the page,
 * which then only fails once the user tries to save. This guard closes that gap by redirecting to the application root
 * while the feature is off.
 *
 * @param feature the feature toggle that must be active for the route to load
 * @returns a {@link CanActivateFn} that resolves to {@code true} when the feature is active, otherwise a redirect to the
 *          application root
 */
export const featureToggleGuard = (feature: FeatureToggle): CanActivateFn => {
    return (): Observable<boolean | UrlTree> => {
        const featureToggleService = inject(FeatureToggleService);
        const router = inject(Router);
        return featureToggleService.getFeatureToggleActive(feature).pipe(
            take(1),
            map((active) => active || router.createUrlTree(['/'])),
        );
    };
};
