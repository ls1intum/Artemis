import { NgModule } from '@angular/core';

import { ExerciseInfoComponent } from 'app/exercises/shared/exercise-info/exercise-info.component';

@NgModule({
    exports: [ExerciseInfoComponent],
    imports: [ExerciseInfoComponent],
})
export class ArtemisExerciseInfoModule {}
