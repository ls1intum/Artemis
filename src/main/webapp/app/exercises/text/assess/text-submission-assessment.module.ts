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
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { ManualTextblockSelectionComponent } from 'app/exercises/text/assess/manual-textblock-selection/manual-textblock-selection.component';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { TextSharedModule } from 'app/exercises/text/shared/text-shared.module';
import { TextFeedbackConflictsComponent } from 'app/exercises/text/assess/conflicts/text-feedback-conflicts.component';
import { TextFeedbackConflictsHeaderComponent } from 'app/exercises/text/assess/conflicts/conflicts-header/text-feedback-conflicts-header.component';
import { TextAssessmentDashboardComponent } from 'app/exercises/text/assess/text-assessment-dashboard/text-assessment-dashboard.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { TextblockFeedbackDropdownComponent } from 'app/exercises/text/assess/textblock-feedback-editor/dropdown/textblock-feedback-dropdown.component';
import { ArtemisAssessmentProgressLabelModule } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.module';

const ENTITY_STATES = [...textSubmissionAssessmentRoutes];

@NgModule({
    imports: [
        CommonModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisComplaintsForTutorModule,
        ArtemisSharedComponentModule,
        ArtemisAssessmentSharedModule,
        AssessmentInstructionsModule,
        ArtemisConfirmIconModule,
        TextSharedModule,
        ArtemisGradingInstructionLinkIconModule,
        SubmissionResultStatusModule,
        ArtemisAssessmentProgressLabelModule,
    ],
    declarations: [
        TextSubmissionAssessmentComponent,
        TextAssessmentAreaComponent,
        TextblockAssessmentCardComponent,
        TextblockFeedbackEditorComponent,
        ManualTextblockSelectionComponent,
        TextFeedbackConflictsComponent,
        TextFeedbackConflictsHeaderComponent,
        TextAssessmentDashboardComponent,
        TextblockFeedbackDropdownComponent,
    ],
    exports: [TextAssessmentAreaComponent],
})
export class ArtemisTextSubmissionAssessmentModule {}
