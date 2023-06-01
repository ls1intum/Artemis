import { CommonModule } from '@angular/common';
import { ModuleWithProviders, NgModule } from '@angular/core';
import { LinkPreviewDirective } from 'app/shared/link-preview/directives/link-preview.directive';

import { HttpClientModule } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { DEFAULT_CONFIG, LinkifyConfigToken, LinkifyModule } from 'app/shared/link-preview/linkify/linkify.module';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';

@NgModule({
    imports: [CommonModule, HttpClientModule, LinkifyModule, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
    exports: [
        // LinkPreviewComponent,
        // LinkPreviewContainerComponent,
        LinkPreviewDirective,
    ],
    declarations: [
        // LinkPreviewComponent,
        // LinkPreviewContainerComponent,
        LinkPreviewDirective,
    ],
})
export class LinkPreviewModule {
    static forRoot(): ModuleWithProviders<any> {
        return {
            ngModule: LinkPreviewModule,
            providers: [
                LinkPreviewService,
                LinkifyService,
                {
                    provide: LinkifyConfigToken,
                    useValue: DEFAULT_CONFIG,
                },
            ],
        };
    }
}
