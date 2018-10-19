import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import {
    TextExerciseComponent,
    TextExerciseDetailComponent,
    TextExerciseUpdateComponent,
    TextExerciseDeletePopupComponent,
    TextExerciseDeleteDialogComponent,
    textExerciseRoute,
    textExercisePopupRoute
} from './';

const ENTITY_STATES = [...textExerciseRoute, ...textExercisePopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        TextExerciseComponent,
        TextExerciseDetailComponent,
        TextExerciseUpdateComponent,
        TextExerciseDeleteDialogComponent,
        TextExerciseDeletePopupComponent
    ],
    entryComponents: [
        TextExerciseComponent,
        TextExerciseUpdateComponent,
        TextExerciseDeleteDialogComponent,
        TextExerciseDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSTextExerciseModule {}
