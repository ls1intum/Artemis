import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';

@NgModule({
    exports: [ExerciseUpdateWarningComponent],
    imports: [CommonModule, ExerciseUpdateWarningComponent],
})
export class ArtemisExerciseUpdateWarningModule {}
