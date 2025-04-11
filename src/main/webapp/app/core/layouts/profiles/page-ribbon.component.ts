import { Component, OnInit, inject } from '@angular/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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
        if (this.profileService.isDevelopment()) {
            this.ribbonEnv = 'dev';
        }
        if (this.profileService.isProduction() && this.profileService.isTestServer()) {
            this.ribbonEnv = 'test';
        }
    }
}
