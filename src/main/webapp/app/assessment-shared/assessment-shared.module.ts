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
import { ExternalSubmissionButtonComponent, ExternalSubmissionDialogComponent } from 'app/assessment-shared/external-submission';
import { ExampleSubmissionService } from 'app/entities/example-submission/example-submission.service';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule, ArtemisSharedComponentModule],
    declarations: [
        AssessmentHeaderComponent,
        AssessmentLayoutComponent,
        AssessmentComplaintAlertComponent,
        AssessmentGeneralFeedbackComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        ExternalSubmissionButtonComponent,
        ExternalSubmissionDialogComponent,
    ],
    entryComponents: [ExternalSubmissionDialogComponent],
    exports: [
        AssessmentLayoutComponent,
        AssessmentGeneralFeedbackComponent,
        ScoreDisplayComponent,
        AssessmentDetailComponent,
        AssessmentFiltersComponent,
        ExternalSubmissionButtonComponent,
    ],
    providers: [JhiAlertService, ExampleSubmissionService],
})
export class ArtemisAssessmentSharedModule {}
