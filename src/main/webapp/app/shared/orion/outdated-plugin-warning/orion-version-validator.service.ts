import { Injectable, inject } from '@angular/core';
import { isOrion } from 'app/shared/orion/orion';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { filter, first, map } from 'rxjs/operators';
import { compare } from 'compare-versions';
import { Observable, of } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

@Injectable({
    providedIn: 'root',
})
export class OrionVersionValidator {
    private profileService = inject(ProfileService);
    private router = inject(Router);

    isOrion = isOrion;
    private minVersion: string;
    private isValidVersion: boolean;

    /**
     * Validates the installed Orion plugin version against the allowed version range. This will not validate anything
     * if Artemis is not opened by Orion and just return true in this case!
     * Otherwise, checks the loaded profile, which includes the allowed version range and automatically routes
     * to an error page if the installed version is incompatible.
     */
    validateOrionVersion(): Observable<boolean> {
        if (this.isOrion) {
            return this.validate();
        } else {
            return of(true);
        }
    }

    private validate(): Observable<boolean> {
        if (this.isValidVersion !== undefined) {
            return of(this.isValidVersion);
        }

        const userAgent = window.navigator.userAgent;
        const orionVersionArray = this.extractVersionFromUserAgent(userAgent);
        if (orionVersionArray.length === 2) {
            const usedVersion = orionVersionArray[1];
            return this.fetchProfileInfoAndCompareVersions(usedVersion);
        } else {
            this.router.navigateByUrl(`/orion-outdated?versionString=soOldThatThereIsNoVersion`);
            this.isValidVersion = false;
            return of(this.isValidVersion);
        }
    }

    private fetchProfileInfoAndCompareVersions(usedVersion: string): Observable<boolean> {
        return this.profileService.getProfileInfo().pipe(
            filter(Boolean),
            first(),
            map((info: ProfileInfo) => {
                this.minVersion = info.allowedMinimumOrionVersion;
                this.isValidVersion = this.versionInBounds(usedVersion);
                return this.isValidVersion;
            }),
        );
    }

    private extractVersionFromUserAgent(userAgent: string): string[] {
        return userAgent
            .split(' ')
            .find((spec: string) => spec.includes('Orion') || spec.includes('IntelliJ'))!
            .split('/');
    }

    private versionInBounds(usedVersion: string): boolean {
        if (!compare(usedVersion, this.minVersion, '>=')) {
            this.router.navigateByUrl(`/orion-outdated?versionString=${usedVersion}`);
            return false;
        }
        return true;
    }
}
