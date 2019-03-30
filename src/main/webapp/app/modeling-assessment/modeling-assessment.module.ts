import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModelingAssessmentComponent } from 'app/modeling-assessment/modeling-assessment.component';
import { ScoreDisplayComponent } from './score-display/score-display.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@NgModule({
    declarations: [ModelingAssessmentComponent, ScoreDisplayComponent],
    exports: [ModelingAssessmentComponent, ScoreDisplayComponent],
    imports: [CommonModule, FontAwesomeModule],
})
export class ModelingAssessmentModule {}
