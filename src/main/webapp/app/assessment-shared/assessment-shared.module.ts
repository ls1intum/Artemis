import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';

import { AssessmentHeaderComponent, AssessmentComplaintAlertComponent, AssessmentLayoutComponent } from '.';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisComplaintsForTutorModule],
    declarations: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent],
    exports: [AssessmentLayoutComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
