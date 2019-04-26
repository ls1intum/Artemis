import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintsForTutorComponent } from './complaints-for-tutor.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule],
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
export class ArTEMiSComplaintsForTutorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
