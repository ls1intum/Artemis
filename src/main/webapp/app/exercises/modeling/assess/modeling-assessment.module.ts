import { NgModule } from '@angular/core';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisAssessmentSharedModule, ArtemisModelingEditorModule],
    declarations: [ModelingAssessmentComponent],
    exports: [ModelingAssessmentComponent],
})
export class ModelingAssessmentModule {}
