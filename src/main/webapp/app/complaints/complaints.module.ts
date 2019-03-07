import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from '../shared';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintsComponent } from './complaints.component';
import { MomentModule } from 'angular2-moment';
import { ClipboardModule } from 'ngx-clipboard';

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule],
    declarations: [ComplaintsComponent],
    exports: [],
    providers: [,
        JhiAlertService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ComplaintsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
