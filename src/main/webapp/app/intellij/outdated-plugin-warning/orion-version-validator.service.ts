import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { isIntelliJ } from 'app/intellij/intellij';
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
    constructor(private profileService: ProfileService, private window: WindowRef, private router: Router) {}

    /**
     * Validates the installed Orion plugin version against the allowed version range. This will not validate anything
     * if Artemis is not opened by Orion and just return true in this case!
     * Otherwise, checks the loaded profile, which includes the allowed version range and automatically routes
     * to an error page if the installed version is incompatible.
     */
    validateOrionVersion(): Observable<boolean | undefined> {
        if (isIntelliJ) {
            return this.validate();
        } else {
            return of(true);
        }
    }

    private validate(): Observable<boolean | undefined> {
        const userAgent = this.window.nativeWindow.navigator.userAgent;
        const orionVersionArray = userAgent
            .split(' ')
            .find((spec: string) => spec.includes('IntelliJ'))
            .split('/');
        if (orionVersionArray.length === 2) {
            const validationSubject = new BehaviorSubject<boolean | undefined>(undefined);
            const usedVersion = orionVersionArray[1];
            this.profileService
                .getProfileInfo()
                .pipe(
                    filter(Boolean),
                    first(),
                    map((info: ProfileInfo) => {
                        const min = info.allowedOrionVersions.from;
                        const max = info.allowedOrionVersions.to;
                        if (!(compare(usedVersion, min, '>=') && compare(usedVersion, max, '<='))) {
                            this.router.navigateByUrl(`/orionOutdated?versionString=${usedVersion}`);
                            return true;
                        }
                        return false;
                    }),
                )
                .subscribe(valid => validationSubject.next(valid));

            return validationSubject;
        } else {
            this.router.navigateByUrl(`/orionOutdated?versionString=soOldThatThereIsNoVersion`);
            return of(false);
        }
    }
}
