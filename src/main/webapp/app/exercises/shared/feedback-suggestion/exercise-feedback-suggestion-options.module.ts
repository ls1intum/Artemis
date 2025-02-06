import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.component';

@NgModule({
    imports: [ArtemisSharedCommonModule, ExerciseFeedbackSuggestionOptionsComponent],
    exports: [ExerciseFeedbackSuggestionOptionsComponent],
})
export class ExerciseFeedbackSuggestionOptionsModule {}
