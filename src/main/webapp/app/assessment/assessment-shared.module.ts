import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { AssessmentComplaintAlertComponent } from './assessment-complaint-alert/assessment-complaint-alert.component';
import { AssessmentDetailComponent } from './assessment-detail/assessment-detail.component';
import { AssessmentHeaderComponent } from './assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from './assessment-layout/assessment-layout.component';
import { ScoreDisplayComponent } from '../shared/score-display/score-display.component';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/assessment-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { assessmentLocksRoute } from 'app/assessment/assessment-locks/assessment-locks.route';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...assessmentLocksRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisComplaintsForTutorModule,
        ArtemisSharedComponentModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisMarkdownModule,
        ArtemisGradingInstructionLinkIconModule,
    ],
    declarations: [
        AssessmentHeaderComponent,
        AssessmentLayoutComponent,
        AssessmentComplaintAlertComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        ExternalSubmissionButtonComponent,
        ExternalSubmissionDialogComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
        AssessmentCorrectionRoundBadgeComponent,
    ],
    exports: [
        AssessmentLayoutComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        ExternalSubmissionButtonComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
        AssessmentCorrectionRoundBadgeComponent,
    ],
})
export class ArtemisAssessmentSharedModule {}
