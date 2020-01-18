import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { isOrion } from 'app/orion/orion';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { filter, first, map } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';
import { compare } from 'compare-versions';
import { BehaviorSubject, Observable, of } from 'rxjs';

export type AllowedOrionVersionRange = {
    from: string;
    to: string;
};

@Injectable({
    providedIn: 'root',
})
export class OrionVersionValidator {
    private minVersion: string;
    private maxVersion: string;

    constructor(private profileService: ProfileService, private window: WindowRef, private router: Router) {}

    /**
     * Validates the installed Orion plugin version against the allowed version range. This will not validate anything
     * if Artemis is not opened by Orion and just return true in this case!
     * Otherwise, checks the loaded profile, which includes the allowed version range and automatically routes
     * to an error page if the installed version is incompatible.
     */
    validateOrionVersion(): Observable<boolean | undefined> {
        if (isOrion) {
            return this.validate();
        } else {
            return of(true);
        }
    }

    private validate(): Observable<boolean | undefined> {
        const userAgent = this.window.nativeWindow.navigator.userAgent;
        const orionVersionArray = this.extractVersionFromUserAgent(userAgent);
        if (orionVersionArray.length === 2) {
            const usedVersion = orionVersionArray[1];
            if (this.minVersion !== undefined) {
                return of(this.versionInBounds(usedVersion));
            } else {
                return this.fetchProfileInfoAndCompareVersions(usedVersion);
            }
        } else {
            this.router.navigateByUrl(`/orionOutdated?versionString=soOldThatThereIsNoVersion`);
            return of(false);
        }
    }

    private fetchProfileInfoAndCompareVersions(usedVersion: string): Observable<boolean | undefined> {
        const validationSubject = new BehaviorSubject<boolean | undefined>(undefined);
        this.profileService
            .getProfileInfo()
            .pipe(
                filter(Boolean),
                first(),
                map((info: ProfileInfo) => {
                    this.minVersion = info.allowedOrionVersions.from;
                    this.maxVersion = info.allowedOrionVersions.to;
                    return this.versionInBounds(usedVersion);
                }),
            )
            .subscribe(valid => validationSubject.next(valid));

        return validationSubject;
    }

    private extractVersionFromUserAgent(userAgent: string): string[] {
        return userAgent
            .split(' ')
            .find((spec: string) => spec.includes('IntelliJ'))!!
            .split('/');
    }

    private versionInBounds(usedVersion: string): boolean {
        if (!(compare(usedVersion, this.minVersion, '>=') && compare(usedVersion, this.maxVersion, '<='))) {
            this.router.navigateByUrl(`/orionOutdated?versionString=${usedVersion}`);
            return false;
        }
        return true;
    }
}
