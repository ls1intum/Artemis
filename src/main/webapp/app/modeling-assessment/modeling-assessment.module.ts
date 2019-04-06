import { NgModule } from '@angular/core';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';
import { ScoreDisplayComponent } from './score-display/score-display.component';
import { ArTEMiSSharedModule } from 'app/shared';

@NgModule({
    declarations: [ModelingAssessmentComponent, ScoreDisplayComponent],
    exports: [ModelingAssessmentComponent, ScoreDisplayComponent],
    imports: [ArTEMiSSharedModule],
})
export class ModelingAssessmentModule {}
