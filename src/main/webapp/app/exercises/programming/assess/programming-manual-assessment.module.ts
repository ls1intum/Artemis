import { NgModule } from '@angular/core';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisGradingInstructionLinkIconModule } from 'app/shared/grading-instruction-link-icon/grading-instruction-link-icon.module';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback-suggestion.component';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { FeedbackContentPipe } from 'app/shared/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/shared/pipes/quote.pipe';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        FormDateTimePickerModule,
        ArtemisAssessmentSharedModule,
        ArtemisGradingInstructionLinkIconModule,
        ArtemisFeedbackModule,
        FeedbackContentPipe,
        QuotePipe,
    ],
    declarations: [CodeEditorTutorAssessmentInlineFeedbackComponent, CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent],
    exports: [CodeEditorTutorAssessmentInlineFeedbackComponent, CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent],
})
export class ArtemisProgrammingManualAssessmentModule {}
