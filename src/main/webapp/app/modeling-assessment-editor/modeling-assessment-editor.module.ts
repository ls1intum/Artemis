import { NgModule } from '@angular/core';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from 'app/shared';
import { RouterModule } from '@angular/router';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment-editor/modeling-assessment-editor.component';
import { HomeComponent } from 'app/home';
import { ArtemisResultModule, ResultComponent, ResultDetailComponent } from 'app/entities/result';
import { JhiMainComponent } from 'app/layouts';
import { SortByModule } from 'app/components/pipes';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { modelingAssessmentRoutes } from 'app/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { ModelingAssessmentConflictComponent } from 'app/modeling-assessment-editor/modeling-assessment-conflict/modeling-assessment-conflict.component';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment-editor/modeling-assessment-dashboard/modeling-assessment-dashboard.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared';

const ENTITY_STATES = [...modelingAssessmentRoutes];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisResultModule,
        AssessmentInstructionsModule,
        ModelingAssessmentModule,
        FontAwesomeModule,
        ArtemisComplaintsForTutorModule,
        ArtemisAssessmentSharedModule,
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
})
export class ArtemisModelingAssessmentEditorModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
