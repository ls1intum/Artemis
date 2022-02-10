import { NgModule } from '@angular/core';
import { ProgrammingExerciseLifecycleComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { OwlDateTimeModule } from '@danielmoncada/angular-datetime-picker';

@NgModule({
    imports: [ArtemisSharedComponentModule, OwlDateTimeModule, ArtemisSharedModule],
    declarations: [ProgrammingExerciseLifecycleComponent, ProgrammingExerciseTestScheduleDatePickerComponent],
    exports: [ProgrammingExerciseLifecycleComponent],
})
export class ArtemisProgrammingExerciseLifecycleModule {}
