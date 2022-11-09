import { Injectable } from '@angular/core';
import { CanLoad, Route } from '@angular/router';
import { lastValueFrom } from 'rxjs';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';

@Injectable({
    providedIn: 'root',
})
export class ProfileAccessService implements CanLoad {
    constructor(private profileToggleService: ProfileToggleService) {}

    canLoad(route: Route): Promise<boolean> {
        const profile: ProfileToggle = route.data?.profile;

        return lastValueFrom(this.profileToggleService.getProfileToggleActive(profile));
    }
}
