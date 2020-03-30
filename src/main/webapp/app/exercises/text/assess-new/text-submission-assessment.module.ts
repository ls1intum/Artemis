import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { textSubmissionAssessmentRoutes } from './text-submission-assessment.route';
import { TextSubmissionAssessmentComponent } from './text-submission-assessment.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { TextAssessmentAreaComponent } from './text-assessment-area/text-assessment-area.component';
import { TextblockAssessmentCardComponentComponent } from './textblock-assessment-card/textblock-assessment-card-component.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';

const ENTITY_STATES = [...textSubmissionAssessmentRoutes];

@NgModule({
    imports: [CommonModule, RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, ArtemisAssessmentSharedModule, AssessmentInstructionsModule],
    declarations: [TextSubmissionAssessmentComponent, TextAssessmentAreaComponent, TextblockAssessmentCardComponentComponent, TextblockFeedbackEditorComponent],
})
export class ArtemisTextSubmissionAssessmentModule {}
