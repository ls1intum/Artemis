import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';

import { AssessmentHeaderComponent } from './assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from './assessment-layout/assessment-layout.component';
import { AssessmentComplaintAlertComponent } from './assessment-complaint-alert/assessment-complaint-alert.component';
import { ScoreDisplayComponent } from '../shared/score-display/score-display.component';
import { AssessmentDetailComponent } from './assessment-detail/assessment-detail.component';
import { AssessmentFiltersComponent } from 'app/assessment/assessment-filters/assessment-filters.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { AssessmentLocksComponent } from 'app/assessment/assessment-locks/assessment-locks.component.ts';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { RouterModule } from '@angular/router';
import { assessmentLocksRoute } from 'app/assessment/assessment-locks/assessment-locks.route';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const ENTITY_STATES = [...assessmentLocksRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisComplaintsForTutorModule,
        ArtemisSharedComponentModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisMarkdownModule,
    ],
    declarations: [
        AssessmentHeaderComponent,
        AssessmentLayoutComponent,
        AssessmentComplaintAlertComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        AssessmentWarningComponent,
        ExternalSubmissionButtonComponent,
        ExternalSubmissionDialogComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
    ],
    exports: [
        AssessmentLayoutComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        AssessmentWarningComponent,
        ExternalSubmissionButtonComponent,
        AssessmentLocksComponent,
        UnreferencedFeedbackComponent,
    ],
})
export class ArtemisAssessmentSharedModule {}
