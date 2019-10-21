import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';

import { AssessmentHeaderComponent } from 'app/assessment-shared/assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from 'app/assessment-shared/assessment-layout/assessment-layout.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment-shared/assessment-complaint-alert/assessment-complaint-alert.component';
import { AssessmentDetailComponent } from 'app/assessment-shared/assessment-detail/assessment-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule],
    declarations: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent, AssessmentDetailComponent],
    exports: [AssessmentLayoutComponent, AssessmentDetailComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
