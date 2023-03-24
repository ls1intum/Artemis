import { Injectable } from '@angular/core';
import { CanLoad, Route } from '@angular/router';
import { Observable } from 'rxjs';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';

@Injectable({
    providedIn: 'root',
})
export class ProfileAccessService implements CanLoad {
    constructor(private profileToggleService: ProfileToggleService) {}

    canLoad(route: Route): Observable<boolean> {
        const profile: ProfileToggle = route.data?.profile;
        return this.profileToggleService.getProfileToggleActive(profile);
    }
}
