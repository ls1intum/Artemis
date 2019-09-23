import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';

import { AssessmentHeaderComponent } from 'app/assessment-shared/assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from 'app/assessment-shared/assessment-layout/assessment-layout.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment-shared/assessment-complaint-alert/assessment-complaint-alert.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule],
    declarations: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent],
    exports: [AssessmentLayoutComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
