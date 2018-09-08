import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ExerciseComponent,
    ExerciseDetailComponent,
    ExerciseUpdateComponent,
    ExerciseDeletePopupComponent,
    ExerciseDeleteDialogComponent,
    exerciseRoute,
    exercisePopupRoute
} from './';

const ENTITY_STATES = [...exerciseRoute, ...exercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ExerciseComponent,
        ExerciseDetailComponent,
        ExerciseUpdateComponent,
        ExerciseDeleteDialogComponent,
        ExerciseDeletePopupComponent
    ],
    entryComponents: [ExerciseComponent, ExerciseUpdateComponent, ExerciseDeleteDialogComponent, ExerciseDeletePopupComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSExerciseModule {}
