import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ModelingExerciseComponent,
    ModelingExerciseDetailComponent,
    ModelingExerciseUpdateComponent,
    ModelingExerciseDeletePopupComponent,
    ModelingExerciseDeleteDialogComponent,
    modelingExerciseRoute,
    modelingExercisePopupRoute
} from './';

const ENTITY_STATES = [...modelingExerciseRoute, ...modelingExercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ModelingExerciseComponent,
        ModelingExerciseDetailComponent,
        ModelingExerciseUpdateComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExerciseDeletePopupComponent
    ],
    entryComponents: [
        ModelingExerciseComponent,
        ModelingExerciseUpdateComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExerciseDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSModelingExerciseModule {}
