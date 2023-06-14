import { CommonModule } from '@angular/common';
import { ModuleWithProviders, NgModule } from '@angular/core';

import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';

@NgModule({
    imports: [CommonModule],
})
export class LinkifyModule {
    static forRoot(): ModuleWithProviders<LinkifyModule> {
        return {
            ngModule: LinkifyModule,
            providers: [LinkifyService],
        };
    }
}
