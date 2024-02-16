import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

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
        try {
            const profileInfo = await firstValueFrom(this.profileService.getProfileInfo());

            if (profileInfo) {
                this.localVCActive = profileInfo?.activeProfiles.includes(PROFILE_LOCALVC);
            }

            if (!this.localVCActive) {
                this.router.navigate(['/']);
                return false;
            }
            return true;
        } catch (error) {
            console.error('Error fetching profile information:', error);
            this.router.navigate(['/']);
            return false;
        }
    }
}
