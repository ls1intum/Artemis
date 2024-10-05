import { Component, OnInit } from '@angular/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-page-ribbon',
    template: `
        <div class="box">
            @if (ribbonEnv) {
                <div class="ribbon ribbon-top-left">
                    <span jhiTranslate="global.ribbon.{{ ribbonEnv }}">{{ ribbonEnv }}</span>
                </div>
            }
        </div>
    `,
    styleUrls: ['page-ribbon.scss'],
    imports: [ArtemisSharedCommonModule],
    standalone: true,
})
export class PageRibbonComponent implements OnInit {
    ribbonEnv: string;

    constructor(private profileService: ProfileService) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                if (profileInfo.inDevelopment) {
                    this.ribbonEnv = 'dev';
                }
                if (profileInfo.inProduction && profileInfo.testServer) {
                    this.ribbonEnv = 'test';
                }
            }
        });
    }
}
