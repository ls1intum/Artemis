import { Service, inject } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Service()
export class GocastGuard implements CanActivate {
    private readonly profileService = inject(ProfileService);
    private readonly router = inject(Router);

    canActivate(): boolean | UrlTree {
        if (!this.profileService.isGocastEnabled()) {
            return this.router.createUrlTree(['/courses']);
        }
        return true;
    }
}
