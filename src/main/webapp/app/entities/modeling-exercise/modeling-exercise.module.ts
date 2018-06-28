import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    ModelingExerciseService,
    ModelingExercisePopupService,
    ModelingExerciseComponent,
    ModelingExerciseDetailComponent,
    ModelingExerciseDialogComponent,
    ModelingExercisePopupComponent,
    ModelingExerciseDeletePopupComponent,
    ModelingExerciseDeleteDialogComponent,
    modelingExerciseRoute,
    modelingExercisePopupRoute,
} from './';

const ENTITY_STATES = [
    ...modelingExerciseRoute,
    ...modelingExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ModelingExerciseComponent,
        ModelingExerciseDetailComponent,
        ModelingExerciseDialogComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExercisePopupComponent,
        ModelingExerciseDeletePopupComponent,
    ],
    entryComponents: [
        ModelingExerciseComponent,
        ModelingExerciseDialogComponent,
        ModelingExercisePopupComponent,
        ModelingExerciseDeleteDialogComponent,
        ModelingExerciseDeletePopupComponent,
    ],
    providers: [
        ModelingExerciseService,
        ModelingExercisePopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSModelingExerciseModule {}
