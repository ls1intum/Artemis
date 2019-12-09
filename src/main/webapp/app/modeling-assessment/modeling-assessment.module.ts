import { NgModule } from '@angular/core';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisAssessmentSharedModule } from 'app/assessment-shared/assessment-shared.module';

@NgModule({
    declarations: [ModelingAssessmentComponent],
    exports: [ModelingAssessmentComponent],
    imports: [ArtemisSharedModule, ArtemisAssessmentSharedModule],
})
export class ModelingAssessmentModule {}
