import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent } from 'app/exercises/shared/exercise/exercise-lti-configuration-dialog.component';
import { exercisePopupRoute } from 'app/exercises/shared/exercise/exercise.route';

const ENTITY_STATES = [...exercisePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent],
    entryComponents: [ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent],
})
export class ArtemisExerciseModule {}
