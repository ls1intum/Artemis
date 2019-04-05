import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { RouterModule } from '@angular/router';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment-editor/modeling-assessment-editor.component';
import { HomeComponent } from 'app/home';
import { ArTEMiSResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { JhiMainComponent } from 'app/layouts';
import { SortByModule } from 'app/components/pipes';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { modelingAssessmentRoutes } from 'app/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArTEMiSComplaintsForTutorModule } from 'app/complaints-for-tutor';
import {ModelingAssessmentConflictComponent} from "app/modeling-assessment-editor/modeling-assessment-conflict/modeling-assessment-conflict.component";

const ENTITY_STATES = [...modelingAssessmentRoutes];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArTEMiSResultModule,
        AssessmentInstructionsModule,
        ModelingAssessmentModule,
        FontAwesomeModule,
        ArTEMiSComplaintsForTutorModule,
    ],
    declarations: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent, ModelingAssessmentConflictComponent],
    entryComponents: [
        HomeComponent,
        ResultComponent,
        ResultDetailComponent,
        ModelingAssessmentDashboardComponent,
        JhiMainComponent,
        ModelingAssessmentEditorComponent,
        ModelingAssessmentConflictComponent,
    ],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSModelingAssessmentEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
