import { NgModule } from '@angular/core';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';

@NgModule({
    imports: [ArtemisAssessmentSharedModule, ArtemisModelingEditorModule, ModelingAssessmentComponent],
    exports: [ModelingAssessmentComponent],
})
export class ModelingAssessmentModule {}
