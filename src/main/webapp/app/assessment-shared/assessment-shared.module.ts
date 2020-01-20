import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';

import { AssessmentHeaderComponent } from './assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from './assessment-layout/assessment-layout.component';
import { AssessmentComplaintAlertComponent } from './assessment-complaint-alert/assessment-complaint-alert.component';
import { AssessmentGeneralFeedbackComponent } from './assessment-general-feedback/assessment-general-feedback.component';
import { ScoreDisplayComponent } from './score-display/score-display.component';
import { AssessmentDetailComponent } from './assessment-detail/assessment-detail.component';
import { AssessmentFiltersComponent } from 'app/assessment-shared/assessment-filters/assessment-filters.component';
import { AssessmentWarningComponent } from 'app/assessment-shared/assessment-warning/assessment-warning.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule],
    declarations: [
        AssessmentHeaderComponent,
        AssessmentLayoutComponent,
        AssessmentComplaintAlertComponent,
        AssessmentGeneralFeedbackComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        AssessmentWarningComponent,
    ],
    exports: [
        AssessmentLayoutComponent,
        AssessmentGeneralFeedbackComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        AssessmentWarningComponent,
    ],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
