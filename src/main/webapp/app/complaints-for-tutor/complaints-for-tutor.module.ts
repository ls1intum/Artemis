import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiAlertService } from 'ng-jhipster';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';

import { JhiLanguageHelper } from 'app/core';
import { ArtemisSharedModule } from 'app/shared';
import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { ComplaintService } from 'app/entities/complaint';
import { ComplaintResponseService } from 'app/entities/complaint-response';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule],
    declarations: [ComplaintsForTutorComponent],
    exports: [ComplaintsForTutorComponent],
    providers: [
        JhiAlertService,
        ComplaintService,
        ComplaintResponseService,
        {
            provide: JhiLanguageService,
            useClass: JhiLanguageService,
        },
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisComplaintsForTutorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
