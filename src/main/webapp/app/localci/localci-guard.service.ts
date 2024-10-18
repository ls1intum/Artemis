import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

@Injectable({
    providedIn: 'root',
})
export class LocalCIGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    localCIActive: boolean = false;

    async canActivate(): Promise<boolean> {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.localCIActive = profileInfo?.activeProfiles.includes(PROFILE_LOCALCI);
            }
        });

        if (!this.localCIActive) {
            this.router.navigate(['/course-management']);
            return false;
        }
        return true;
    }
}
