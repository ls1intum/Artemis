import { NgModule } from '@angular/core';

import { AssessmentHeaderComponent } from './assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from './assessment-layout/assessment-layout.component';
import { AssessmentComplaintAlertComponent } from './assessment-complaint-alert/assessment-complaint-alert.component';
import { ScoreDisplayComponent } from '../shared/score-display/score-display.component';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/unreferenced-feedback-detail/unreferenced-feedback-detail.component';

import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component';
import { RouterModule } from '@angular/router';
import { assessmentLocksRoute } from 'app/assessment/assessment-locks/assessment-locks.route';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { AssessmentNoteComponent } from 'app/assessment/assessment-note/assessment-note.component';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';

const ENTITY_STATES = [...assessmentLocksRoute];

@NgModule({
    imports: [
        ComplaintsForTutorComponent,

        RouterModule.forChild(ENTITY_STATES),
        ArtemisFeedbackModule,
        FeedbackContentPipe,
        QuotePipe,
        AssessmentHeaderComponent,
        AssessmentLayoutComponent,
        AssessmentComplaintAlertComponent,
        ScoreDisplayComponent,
        UnreferencedFeedbackDetailComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
        AssessmentCorrectionRoundBadgeComponent,
        AssessmentNoteComponent,
    ],
    exports: [
        AssessmentLayoutComponent,
        ScoreDisplayComponent,
        UnreferencedFeedbackDetailComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
        AssessmentCorrectionRoundBadgeComponent,
    ],
})
export class ArtemisAssessmentSharedModule {}
