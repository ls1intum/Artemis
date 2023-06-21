import { CommonModule } from '@angular/common';
import { ModuleWithProviders, NgModule } from '@angular/core';

import { HttpClientModule } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { LinkifyModule } from 'app/shared/link-preview/linkify/linkify.module';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';

@NgModule({
    imports: [CommonModule, HttpClientModule, LinkifyModule, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
})
export class LinkPreviewModule {
    static forRoot(): ModuleWithProviders<any> {
        return {
            ngModule: LinkPreviewModule,
            providers: [LinkPreviewService, LinkifyService],
        };
    }
}
