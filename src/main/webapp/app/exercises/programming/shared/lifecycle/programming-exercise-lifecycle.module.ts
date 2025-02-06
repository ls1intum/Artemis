import { NgModule } from '@angular/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';

import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';

import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { ExerciseFeedbackSuggestionOptionsModule } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.module';

@NgModule({
    imports: [OwlDateTimeModule, ExerciseFeedbackSuggestionOptionsModule, ProgrammingExerciseLifecycleComponent, ProgrammingExerciseTestScheduleDatePickerComponent],
    exports: [ProgrammingExerciseLifecycleComponent],
})
export class ArtemisProgrammingExerciseLifecycleModule {}
