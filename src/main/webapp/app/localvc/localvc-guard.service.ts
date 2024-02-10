import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';

@Injectable({
    providedIn: 'root',
})
export class LocalVCGuard implements CanActivate {
    localVCActive: boolean = false;
    constructor(
        private profileService: ProfileService,
        private router: Router,
    ) {}

    async canActivate(): Promise<boolean> {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.localVCActive = profileInfo?.activeProfiles.includes(PROFILE_LOCALVC);
            }
        });

        if (!this.localVCActive) {
            this.router.navigate(['/']);
            return false;
        }
        return true;
    }
}
