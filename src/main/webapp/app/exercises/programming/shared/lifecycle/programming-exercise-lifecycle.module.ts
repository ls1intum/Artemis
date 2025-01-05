import { NgModule } from '@angular/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ExercisePreliminaryFeedbackOptionsComponent } from 'app/exercises/shared/preliminary-feedback/exercise-preliminary-feedback-options.component';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.component';

@NgModule({
    imports: [ArtemisSharedComponentModule, OwlDateTimeModule, ArtemisSharedModule, ExerciseFeedbackSuggestionOptionsComponent, ExercisePreliminaryFeedbackOptionsComponent],
    declarations: [ProgrammingExerciseLifecycleComponent, ProgrammingExerciseTestScheduleDatePickerComponent],
    exports: [ProgrammingExerciseLifecycleComponent],
})
export class ArtemisProgrammingExerciseLifecycleModule {}
