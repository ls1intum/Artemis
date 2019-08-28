import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

import { ArtemisSharedModule } from 'app/shared';

import { AssessmentHeaderComponent } from '.';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [AssessmentHeaderComponent],
    exports: [AssessmentHeaderComponent],
    providers: [JhiAlertService],
})
export class ArtemisAssessmentSharedModule {}
