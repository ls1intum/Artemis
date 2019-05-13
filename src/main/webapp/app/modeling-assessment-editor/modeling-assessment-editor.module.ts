import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSSharedModule } from 'app/shared';
import { RouterModule } from '@angular/router';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment-editor/modeling-assessment-editor.component';
import { ArTEMiSResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { SortByModule } from 'app/components/pipes';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { modelingAssessmentRoutes } from 'app/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment-editor/modeling-assessment-dashboard/modeling-assessment-dashboard.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArTEMiSComplaintsForTutorModule } from 'app/complaints-for-tutor';

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
    entryComponents: [HomeComponent, ResultComponent, ResultDetailComponent, ModelingAssessmentDashboardComponent, JhiMainComponent, ModelingAssessmentEditorComponent],
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
