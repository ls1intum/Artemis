import { NgModule } from '@angular/core';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisModelingAssessmentEditorRoutingModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.route';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';

@NgModule({
    imports: [
        ArtemisResultModule,
        AssessmentInstructionsModule,
        ArtemisAssessmentSharedModule,
        ArtemisModelingAssessmentEditorRoutingModule,
        ModelingAssessmentModule,
        SubmissionResultStatusModule,
        ArtemisAssessmentProgressLabelModule,
        ModelingAssessmentEditorComponent,
    ],
    exports: [ModelingAssessmentEditorComponent],
})
export class ArtemisModelingAssessmentEditorModule {}
