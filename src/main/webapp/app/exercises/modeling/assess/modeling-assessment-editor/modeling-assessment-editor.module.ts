import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisModelingAssessmentEditorRoutingModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisFullscreenModule,
        AssessmentInstructionsModule,
        ArtemisAssessmentSharedModule,
        ArtemisModelingAssessmentEditorRoutingModule,
        ModelingAssessmentModule,
        SubmissionResultStatusModule,
    ],
    declarations: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent],
    exports: [ModelingAssessmentEditorComponent],
})
export class ArtemisModelingAssessmentEditorModule {}
