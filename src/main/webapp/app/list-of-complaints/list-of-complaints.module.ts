import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { JhiAlertService } from 'ng-jhipster';
import { ListOfComplaintsComponent } from './list-of-complaints.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { RouterModule } from '@angular/router';
import { listOfComplaintsRoute } from 'app/list-of-complaints/list-of-complaints.route';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [...listOfComplaintsRoute];

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule],
    declarations: [ListOfComplaintsComponent],
    exports: [ListOfComplaintsComponent],
    providers: [JhiAlertService, ComplaintService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSListOfComplaintsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
