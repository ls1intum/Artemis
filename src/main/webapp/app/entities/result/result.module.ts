import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService } from './';
import { MomentModule } from 'angular2-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule
    ],
    declarations: [
        ResultComponent,
        ResultDetailComponent,
        ResultHistoryComponent
    ],
    exports: [
        ResultComponent,
        ResultDetailComponent,
        ResultHistoryComponent
    ],
    entryComponents: [
        ResultComponent,
        ResultDetailComponent
    ],
    providers: [
        ResultService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSResultModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
