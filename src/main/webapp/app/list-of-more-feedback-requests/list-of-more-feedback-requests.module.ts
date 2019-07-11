import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { JhiAlertService } from 'ng-jhipster';
import { ListOfMoreFeedbackRequestsComponent } from './list-of-more-feedback-requests.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { RouterModule } from '@angular/router';
import { SortByModule } from 'app/components/pipes';
import { listOfMoreFeedbackRequestsRoute } from 'app/list-of-more-feedback-requests/list-of-more-feedback-requests.route';

const ENTITY_STATES = [...listOfMoreFeedbackRequestsRoute];

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [ListOfMoreFeedbackRequestsComponent],
    exports: [ListOfMoreFeedbackRequestsComponent],
    providers: [JhiAlertService, ComplaintService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSListOfMoreFeedbackRequestsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
