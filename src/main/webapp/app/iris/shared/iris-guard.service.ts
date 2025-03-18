import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { first, lastValueFrom, of, switchMap } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class IrisGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    /**
     * Check if the client can activate a route.
     * @return true if Iris is enabled for this instance, false otherwise
     */
    canActivate(): Promise<boolean> {
        return lastValueFrom(
            this.profileService.getProfileInfo().pipe(
                first(),
                switchMap((profileInfo) => {
                    if (profileInfo.activeProfiles.includes(PROFILE_IRIS)) {
                        return of(true);
                    }
                    this.router.navigate(['/']);
                    return of(false);
                }),
            ),
        );
    }
}
