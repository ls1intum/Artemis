import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

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
    standalone: false,
})
export class OrionOutdatedComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private profileService = inject(ProfileService);

    versionString: string;
    allowedMinimumVersion: string;

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
