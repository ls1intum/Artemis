import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { ModelingAssessmentService } from './modeling-assessment.service';
import { RouterModule } from '@angular/router';
import { modelingAssessmentRoutes } from 'app/modeling-assessment/modeling-assessment.route';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment/modeling-assessment-editor.component';
import { HomeComponent } from 'app/home';
import { ArTEMiSResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment/modeling-assessment-dashboard.component';
import { JhiMainComponent } from 'app/layouts';
import { SortByModule } from 'app/components/pipes';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ModelingAssessmentComponent } from './modeling-assessment/modeling-assessment.component';

const ENTITY_STATES = [...modelingAssessmentRoutes];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), NgbModule, SortByModule, ArTEMiSResultModule],
    declarations: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent, ModelingAssessmentComponent],
    entryComponents: [HomeComponent, ResultComponent, ResultDetailComponent, ModelingAssessmentDashboardComponent, JhiMainComponent, ModelingAssessmentEditorComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
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
