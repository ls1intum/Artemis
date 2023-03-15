import { NgModule } from '@angular/core';

import { AssessmentProgressLabelComponent } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [AssessmentProgressLabelComponent],
    exports: [AssessmentProgressLabelComponent],
})
export class ArtemisAssessmentProgressLabelModule {}
