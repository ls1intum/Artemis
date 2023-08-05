import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { AlertService } from 'app/core/util/alert.service';

@Injectable({
    providedIn: 'root',
})
export class ProfileToggleGuard implements CanActivate {
    constructor(private alertService: AlertService, private profileToggleService: ProfileToggleService, private router: Router) {}

    canActivate(route: ActivatedRouteSnapshot): Observable<boolean> {
        const profile: ProfileToggle = route.data?.profile;
        return this.profileToggleService.getProfileToggleActive(profile).pipe(
            tap((activated: boolean) => {
                if (!activated) {
                    this.router.navigate(['/']);
                    this.alertService.addErrorAlert('This functionality is currently not available', 'artemisApp.profileToggle.alerts.routeFunctionalityNotAvailable');
                }
            }),
        );
    }
}
