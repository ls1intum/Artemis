import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';

import { ArtemisSharedModule } from 'app/shared';
import { ResultComponent, ResultDetailComponent, ResultService, UpdatingResultComponent } from './';
import { MomentModule } from 'ngx-moment';
import { ResultHistoryComponent } from 'app/entities/result/result-history.component';
import { ArtemisProgrammingSubmissionModule } from 'app/submission/submission.module';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ArtemisProgrammingSubmissionModule],
    declarations: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    exports: [ResultComponent, UpdatingResultComponent, ResultDetailComponent, ResultHistoryComponent],
    entryComponents: [ResultComponent, UpdatingResultComponent, ResultDetailComponent],
    providers: [ResultService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisResultModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
