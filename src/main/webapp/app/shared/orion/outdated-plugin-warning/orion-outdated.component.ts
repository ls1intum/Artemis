import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-orion-outdated',
    template: `
        <h2 class="text-danger font-weight-bold" jhiTranslate="artemisApp.orion.version.outdated">The version of Orion you are currently using is outdated!</h2>
        <div class="font-weight-bold ">
            {{ 'artemisApp.orion.version.usedVersion' | artemisTranslate }}<span class="badge bg-pill bg-danger">{{ versionString }}</span
            >!
        </div>
        <div>
            {{ 'artemisApp.orion.version.allowedVersion' | artemisTranslate }}<span class="badge bg-pill bg-info">{{ allowedMinimumVersion }}</span>
        </div>
    `,
    imports: [ArtemisSharedModule],
    standalone: true,
})
export class OrionOutdatedComponent implements OnInit {
    versionString: string;
    allowedMinimumVersion: string;

    constructor(
        private activatedRoute: ActivatedRoute,
        private profileService: ProfileService,
    ) {}

    /**
     * On initialization, sets the values of the used version and the minimum allowed version of orion.
     */
    ngOnInit(): void {
        this.activatedRoute.queryParams.subscribe((params) => {
            this.versionString = params['versionString'];
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                this.allowedMinimumVersion = profileInfo.allowedMinimumOrionVersion;
            });
        });
    }
}
