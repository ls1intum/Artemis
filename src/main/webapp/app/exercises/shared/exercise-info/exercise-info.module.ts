import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseInfoComponent } from 'app/exercises/shared/exercise-info/exercise-info.component';

@NgModule({
    exports: [ExerciseInfoComponent],
    imports: [ArtemisSharedModule, ExerciseInfoComponent],
})
export class ArtemisExerciseInfoModule {}
