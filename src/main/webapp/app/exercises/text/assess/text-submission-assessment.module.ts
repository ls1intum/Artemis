import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { textSubmissionAssessmentRoutes } from './text-submission-assessment.route';
import { TextSubmissionAssessmentComponent } from './text-submission-assessment.component';

import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { TextAssessmentAreaComponent } from './text-assessment-area/text-assessment-area.component';
import { TextblockAssessmentCardComponent } from './textblock-assessment-card/textblock-assessment-card.component';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';

import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { TextblockFeedbackDropdownComponent } from 'app/exercises/text/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';

const ENTITY_STATES = [...textSubmissionAssessmentRoutes];

@NgModule({
    imports: [
        CommonModule,
        RouterModule.forChild(ENTITY_STATES),

        ArtemisResultModule,
        ComplaintsForTutorComponent,
        ArtemisSharedComponentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        SubmissionResultStatusModule,
        ArtemisAssessmentProgressLabelModule,
        ArtemisFeedbackModule,
        TextSubmissionAssessmentComponent,
        TextAssessmentAreaComponent,
        TextblockAssessmentCardComponent,
        TextblockFeedbackEditorComponent,
        ManualTextblockSelectionComponent,
        TextblockFeedbackDropdownComponent,
    ],
    exports: [TextAssessmentAreaComponent],
})
export class ArtemisTextSubmissionAssessmentModule {}
