import { Component, OnInit } from '@angular/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from './profile-info.model';

@Component({
    selector: 'jhi-page-ribbon',
    template: `
        <div class="box">
            <div class="ribbon ribbon-top-left" *ngIf="ribbonEnv">
                <span jhiTranslate="global.ribbon.{{ ribbonEnv }}">{{ ribbonEnv }}</span>
            </div>
        </div>
    `,
    styleUrls: ['page-ribbon.scss'],
})
export class PageRibbonComponent implements OnInit {
    profileInfo: ProfileInfo;
    ribbonEnv: string;

    constructor(private profileService: ProfileService) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe({
            next: (profileInfo) => {
                if (profileInfo) {
                    this.profileInfo = profileInfo;
                    this.ribbonEnv = profileInfo.ribbonEnv;
                    if (profileInfo.inProduction && profileInfo.testServer) {
                        this.ribbonEnv = 'test';
                    }
                }
            },
        });
    }
}
