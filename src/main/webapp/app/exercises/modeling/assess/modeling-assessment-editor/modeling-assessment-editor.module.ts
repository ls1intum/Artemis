import { NgModule } from '@angular/core';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ModelingAssessmentDashboardComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-dashboard.component';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ArtemisModelingAssessmentEditorRoutingModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
        ArtemisAssessmentProgressLabelModule,
    ],
    declarations: [ModelingAssessmentDashboardComponent, ModelingAssessmentEditorComponent],
    exports: [ModelingAssessmentEditorComponent],
})
export class ArtemisModelingAssessmentEditorModule {}
