import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService, UpdatingResultComponent } from './';
import { MomentModule } from 'angular2-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ResultWebsocketService } from './result-websocket.service';

@NgModule({
    imports: [ArTEMiSSharedModule, MomentModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent],
    providers: [ResultService, ResultWebsocketService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
