import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedModule } from 'app/shared';
import {
    ExerciseLtiConfigurationDialogComponent,
    ExerciseLtiConfigurationPopupComponent,
    ExerciseLtiConfigurationService,
    exercisePopupRoute,
    ExercisePopupService,
    ExerciseService,
} from './';

const ENTITY_STATES = [...exercisePopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent],
    entryComponents: [ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent],
    providers: [ExerciseService, ExerciseLtiConfigurationService, ExercisePopupService],
})
export class ArtemisExerciseModule {}
