import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MajorMinorPatch } from 'app/layouts';
import { ProfileService } from 'app/layouts/profiles/profile.service';

export type AllowedOrionVersionRange = {
    from: MajorMinorPatch;
    to: MajorMinorPatch;
};

@Component({
    selector: 'jhi-orion-outdated',
    template: `
        <h2 class="text-danger font-weight-bold">The version of Orion you are currently using is outdated!</h2>
        <div class="font-weight-bold ">
            You are using version <span class="badge badge-pill badge-danger">{{ versionString }}</span
            >!
        </div>
        <div>
            The allowed version range is <span class="badge badge-pill badge-info">{{ allowedVersionStart }}</span> -
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
        });
    }
}
