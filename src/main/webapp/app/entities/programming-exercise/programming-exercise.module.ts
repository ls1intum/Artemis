import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from '../../shared';
import {
    ProgrammingExerciseService,
    ProgrammingExercisePopupService,
    ProgrammingExerciseComponent,
    ProgrammingExerciseDetailComponent,
    ProgrammingExerciseDialogComponent,
    ProgrammingExercisePopupComponent,
    ProgrammingExerciseDeletePopupComponent,
    ProgrammingExerciseDeleteDialogComponent,
    programmingExerciseRoute,
    programmingExercisePopupRoute,
} from './';

const ENTITY_STATES = [
    ...programmingExerciseRoute,
    ...programmingExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTeMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseDialogComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExercisePopupComponent,
        ProgrammingExerciseDeletePopupComponent,
    ],
    entryComponents: [
        ProgrammingExerciseComponent,
        ProgrammingExerciseDialogComponent,
        ProgrammingExercisePopupComponent,
        ProgrammingExerciseDeleteDialogComponent,
        ProgrammingExerciseDeletePopupComponent,
    ],
    providers: [
        ProgrammingExerciseService,
        ProgrammingExercisePopupService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSProgrammingExerciseModule {}
