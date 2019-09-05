import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';

import { AssessmentHeaderComponent, AssessmentComplaintAlertComponent } from '.';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [AssessmentHeaderComponent, AssessmentComplaintAlertComponent],
    exports: [AssessmentHeaderComponent, AssessmentComplaintAlertComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
