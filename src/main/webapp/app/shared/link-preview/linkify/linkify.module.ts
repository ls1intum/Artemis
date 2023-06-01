import { CommonModule } from '@angular/common';
import { Inject, InjectionToken, ModuleWithProviders, NgModule } from '@angular/core';

import { LinkifyConfig } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';
import { LinkifyPipe } from 'app/shared/link-preview/linkify/pipes/linkify.pipe';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';

// TODO: maybe some issues with AoT
export const LinkifyConfigToken = new InjectionToken<LinkifyConfig>('LinkifyConfig');
export const DEFAULT_CONFIG: LinkifyConfig = { enableHash: true, enableMention: true };

@NgModule({
    imports: [CommonModule],
    exports: [LinkifyPipe],
    declarations: [LinkifyPipe],
})
export class LinkifyModule {
    static forRoot(config: LinkifyConfig = DEFAULT_CONFIG): ModuleWithProviders<LinkifyModule> {
        return {
            ngModule: LinkifyModule,
            providers: [
                LinkifyService,
                {
                    provide: LinkifyConfigToken,
                    useValue: config,
                },
            ],
        };
    }

    constructor(
        @Inject(LinkifyConfigToken)
        public config: LinkifyConfig,
    ) {}
}
