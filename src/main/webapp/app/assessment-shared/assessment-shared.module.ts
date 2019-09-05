import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';

import { AssessmentHeaderComponent, AssessmentComplaintAlertComponent, AssessmentLayoutComponent } from '.';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent],
    exports: [AssessmentHeaderComponent, AssessmentLayoutComponent, AssessmentComplaintAlertComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
