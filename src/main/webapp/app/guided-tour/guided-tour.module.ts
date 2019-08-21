import { CUSTOM_ELEMENTS_SCHEMA, NgModule, ErrorHandler } from '@angular/core';
import { ModuleWithProviders } from '@angular/compiler/src/core';
import { JhiLanguageService } from 'ng-jhipster';

import { JhiLanguageHelper } from 'app/core';
import { ArtemisSharedModule } from 'app/shared';
import { GuidedTourService } from './guided-tour.service';
import { GuidedTourComponent } from './guided-tour.component';

@NgModule({
    declarations: [GuidedTourComponent],
    imports: [ArtemisSharedModule],
    exports: [GuidedTourComponent],
    entryComponents: [GuidedTourComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class GuidedTourModule {
    public static forRoot(): ModuleWithProviders {
        return {
            ngModule: GuidedTourModule,
            providers: [ErrorHandler, GuidedTourService],
        };
    }

    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
