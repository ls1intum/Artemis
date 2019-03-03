import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ModelingAssessmentService } from './modeling-assessment.service';

@NgModule({
    imports: [
        ArTEMiSSharedModule
    ],
    declarations: [

    ],
    entryComponents: [

    ],
    providers: [
        ModelingAssessmentService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ]
})
export class ArTEMiSModelingAssessmentModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
