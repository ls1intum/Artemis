import { NgModule } from '@angular/core';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment/modeling-assessment.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';

@NgModule({
    declarations: [ModelingAssessmentComponent],
    exports: [ModelingAssessmentComponent],
    imports: [ArtemisSharedModule, ArtemisAssessmentSharedModule],
})
export class ModelingAssessmentModule {}
