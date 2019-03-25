import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ModelingAssessmentService } from './modeling-assessment.service';
import { RouterModule } from '@angular/router';
import { modelingAssessmentRoutes } from 'app/modeling-assessment/modeling-assessment.route';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';

const ENTITY_STATES = [...modelingAssessmentRoutes];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
    ],
    declarations: [
        ModelingAssessmentComponent
    ],
    entryComponents: [
        ModelingAssessmentComponent
    ],
    providers: [
        ModelingAssessmentService,
        { provide: JhiLanguageService, useClass: JhiLanguageService }
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
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
