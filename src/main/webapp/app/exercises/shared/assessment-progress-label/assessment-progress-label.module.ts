import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { AssessmentProgressLabelComponent } from 'app/exercises/shared/assessment-progress-label/assessment-progress-label.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [AssessmentProgressLabelComponent],
    exports: [AssessmentProgressLabelComponent],
})
export class ArtemisAssessmentProgressLabelModule {}
