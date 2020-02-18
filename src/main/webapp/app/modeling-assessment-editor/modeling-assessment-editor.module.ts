import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { ModelingAssessmentEditorComponent } from 'app/modeling-assessment-editor/modeling-assessment-editor.component';
import { SortByModule } from 'app/components/pipes/sort-by.module';
import { AssessmentInstructionsModule } from 'app/assessment-instructions/assessment-instructions.module';
import { modelingAssessmentRoutes } from 'app/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/modeling-assessment/modeling-assessment.module';
import { ModelingAssessmentConflictComponent } from 'app/modeling-assessment-editor/modeling-assessment-conflict/modeling-assessment-conflict.component';
import { ModelingAssessmentDashboardComponent } from 'app/modeling-assessment-editor/modeling-assessment-dashboard/modeling-assessment-dashboard.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';

const ENTITY_STATES = [...modelingAssessmentRoutes];

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisResultModule,
        AssessmentInstructionsModule,
        ModelingAssessmentModule,
        ArtemisAssessmentSharedModule,
    ],
    declarations: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent, ModelingAssessmentConflictComponent],
    exports: [ModelingAssessmentEditorComponent],
    entryComponents: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent, ModelingAssessmentConflictComponent],
})
export class ArtemisModelingAssessmentEditorModule {}
