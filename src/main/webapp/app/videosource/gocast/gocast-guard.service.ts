import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Injectable({ providedIn: 'root' })
export class GocastGuard implements CanActivate {
    private readonly profileService = inject(ProfileService);
    private readonly router = inject(Router);

    canActivate(): boolean {
        if (!this.profileService.isGocastEnabled()) {
            void this.router.navigate(['/courses']);
            return false;
        }
        return true;
    }
}
