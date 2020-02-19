import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts/profiles/profile-info.model';

@Component({
    selector: 'jhi-orion-outdated',
    template: `
        <h2 class="text-danger font-weight-bold" jhiTranslate="artemisApp.orion.version.outdated">The version of Orion you are currently using is outdated!</h2>
        <div class="font-weight-bold ">
            {{ 'artemisApp.orion.version.usedVersion' | translate }}<span class="badge badge-pill badge-danger">{{ versionString }}</span
            >!
        </div>
        <div>
            {{ 'artemisApp.orion.version.allowedVersion' | translate }}<span class="badge badge-pill badge-info">{{ allowedVersionStart }}</span> -
            <span class="badge badge-pill badge-info">{{ allowedVersionEnd }}</span>
        </div>
    `,
})
export class OrionOutdatedComponent implements OnInit {
    versionString: string;
    allowedVersionStart: string;
    allowedVersionEnd: string;

    constructor(private activatedRoute: ActivatedRoute, private profileService: ProfileService) {}

    ngOnInit(): void {
        this.activatedRoute.queryParams.subscribe(params => {
            this.versionString = params['versionString'];
            this.profileService
                .getProfileInfo()
                .pipe(
                    filter(Boolean),
                    tap((info: ProfileInfo) => {
                        this.allowedVersionStart = info.allowedOrionVersions.from;
                        this.allowedVersionEnd = info.allowedOrionVersions.to;
                    }),
                )
                .subscribe();
        });
    }
}
