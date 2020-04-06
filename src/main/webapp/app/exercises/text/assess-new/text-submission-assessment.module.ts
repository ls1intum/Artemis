import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { textSubmissionAssessmentRoutes } from './text-submission-assessment.route';
import { TextSubmissionAssessmentComponent } from './text-submission-assessment.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { TextAssessmentAreaComponent } from './text-assessment-area/text-assessment-area.component';
import { TextblockAssessmentCardComponent } from './textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';

const ENTITY_STATES = [...textSubmissionAssessmentRoutes];

@NgModule({
    imports: [CommonModule, RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, ArtemisAssessmentSharedModule, AssessmentInstructionsModule, ArtemisConfirmIconModule],
    declarations: [TextSubmissionAssessmentComponent, TextAssessmentAreaComponent, TextblockAssessmentCardComponent, TextblockFeedbackEditorComponent],
})
export class ArtemisTextSubmissionAssessmentModule {}
