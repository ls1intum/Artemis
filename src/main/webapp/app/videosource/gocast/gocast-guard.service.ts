import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { map, take } from 'rxjs/operators';

import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';

@Injectable({
    providedIn: 'root',
})
export class GocastGuard implements CanActivate {
    private featureToggleService = inject(FeatureToggleService);
    private router = inject(Router);

    /**
     * Allow route activation only when the Gocast feature toggle is active.
     * Redirects to /course-management when the feature is disabled.
     *
     * @return an observable that emits true when Gocast is enabled, false otherwise
     */
    canActivate(): Observable<boolean> {
        return this.featureToggleService.getFeatureToggleActive(FeatureToggle.Gocast).pipe(
            take(1),
            map((isActive) => {
                if (!isActive) {
                    this.router.navigate(['/course-management']);
                    return false;
                }
                return true;
            }),
        );
    }
}
