import { Component, OnInit, inject } from '@angular/core';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { TranslateDirective } from '../../language/translate.directive';

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
    imports: [TranslateDirective],
})
export class PageRibbonComponent implements OnInit {
    private profileService = inject(ProfileService);

    ribbonEnv: string;

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
