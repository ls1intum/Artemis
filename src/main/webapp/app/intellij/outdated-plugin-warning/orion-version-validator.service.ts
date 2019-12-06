import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { isIntelliJ } from 'app/intellij/intellij';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';
import { compare } from 'compare-versions';

export type AllowedOrionVersionRange = {
    from: string;
    to: string;
};

@Injectable({
    providedIn: 'root',
})
export class OrionVersionValidator {
    constructor(private profileService: ProfileService, private window: WindowRef, private router: Router) {}

    validateOrionVersion(): void {
        if (isIntelliJ) {
            const userAgent = this.window.nativeWindow.navigator.userAgent;
            const orionVersionArray = userAgent
                .split(' ')
                .find((spec: string) => spec.includes('IntelliJ'))
                .split(':');
            if (orionVersionArray.length === 2) {
                const usedVersion = orionVersionArray[1];
                this.profileService
                    .getProfileInfo()
                    .pipe(
                        filter(Boolean),
                        tap((info: ProfileInfo) => {
                            const min = info.allowedOrionVersions.from;
                            const max = info.allowedOrionVersions.to;
                            if (!(compare(usedVersion, min, '>=') && compare(usedVersion, max, '<='))) {
                                this.router.navigateByUrl(`/orionOutdated?versionString=${usedVersion}`);
                            }
                        }),
                    )
                    .subscribe();
            } else {
                this.router.navigateByUrl(`/orionOutdated?versionString=soOldThatThereIsNoVersion`);
            }
        }
    }
}
