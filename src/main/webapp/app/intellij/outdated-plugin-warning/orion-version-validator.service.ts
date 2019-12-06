import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { isIntelliJ } from 'app/intellij/intellij';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { Router } from '@angular/router';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';

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
                const [major, minor, patch] = orionVersionArray[1].split('.').map((val: string) => +val);
                this.profileService
                    .getProfileInfo()
                    .pipe(
                        filter(Boolean),
                        tap((info: ProfileInfo) => {
                            const min = info.allowedOrionVersions.from;
                            // Assuming we will never have something like 1324.5436.9485 as a version number. Even then, we just have to adapt this
                            const minVersionNumerical = min.major * 1_000_000 + min.minor * 1_000 + min.patch;
                            const currentVersionNumerical = major * 1_000_000 + minor * 1_000 + patch;
                            if (currentVersionNumerical < minVersionNumerical) {
                                this.router.navigateByUrl(`/orionOutdated?versionString=${major}.${minor}.${patch}`);
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
