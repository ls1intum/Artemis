import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';

import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

@Injectable({
    providedIn: 'root',
})
export class LocalCIGuard implements CanActivate {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    canActivate(): boolean {
        if (!this.profileService.isProfileActive(PROFILE_LOCALCI)) {
            this.router.navigate(['/course-management']);
            return false;
        }
        return true;
    }
}
