import { NgModule } from '@angular/core';

import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, FormDateTimePickerModule, ArtemisAssessmentSharedModule, ArtemisGradingInstructionLinkIconModule],
    declarations: [CodeEditorTutorAssessmentInlineFeedbackComponent],
    exports: [CodeEditorTutorAssessmentInlineFeedbackComponent],
})
export class ArtemisProgrammingManualAssessmentModule {}
