import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService, UpdatingResultComponent } from './';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ArTEMiSProgrammingSubmissionModule } from 'app/submission/submission.module';

@NgModule({
    imports: [ArTEMiSSharedModule, MomentModule, ArTEMiSProgrammingSubmissionModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent],
    providers: [ResultService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSResultModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
