import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    ProgrammingExerciseComponent,
    ProgrammingExerciseDetailComponent,
    ProgrammingExerciseUpdateComponent,
    ProgrammingExerciseDeletePopupComponent,
    ProgrammingExerciseDeleteDialogComponent,
    programmingExerciseRoute,
    programmingExercisePopupRoute
} from './';

const ENTITY_STATES = [...programmingExerciseRoute, ...programmingExercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExerciseDeletePopupComponent
    ],
    entryComponents: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseUpdateComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExerciseDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSProgrammingExerciseModule {}
