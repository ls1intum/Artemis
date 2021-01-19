import { NgModule } from '@angular/core';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisAssessmentSharedModule, ArtemisModelingEditorModule],
    declarations: [ModelingAssessmentComponent],
    exports: [ModelingAssessmentComponent],
})
export class ModelingAssessmentModule {}
