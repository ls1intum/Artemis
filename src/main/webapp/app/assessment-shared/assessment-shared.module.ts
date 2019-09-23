import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';

import { AssessmentHeaderComponent, AssessmentComplaintAlertComponent, AssessmentLayoutComponent } from '.';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { AssessmentDetailComponent } from 'app/assessment-shared/assessment-detail/assessment-detail.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule],
    declarations: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent, AssessmentDetailComponent],
    exports: [AssessmentLayoutComponent, AssessmentDetailComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
