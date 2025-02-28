import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { captureException } from '@sentry/angular';

@Injectable({
    providedIn: 'root',
})
export class LocalVCGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    localVCActive = false;

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
            captureException('Error fetching profile information:', error);
            this.router.navigate(['/']);
            return false;
        }
    }
}
