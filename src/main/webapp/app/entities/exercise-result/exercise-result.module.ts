import { NgModule, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTeMiSSharedModule } from 'app/shared';
import { ArTeMiSAdminModule } from 'app/admin/admin.module';
import {
    ExerciseResultComponent,
    ExerciseResultDetailComponent,
    ExerciseResultUpdateComponent,
    ExerciseResultDeletePopupComponent,
    ExerciseResultDeleteDialogComponent,
    exerciseResultRoute,
    exerciseResultPopupRoute
} from './';

const ENTITY_STATES = [...exerciseResultRoute, ...exerciseResultPopupRoute];

@NgModule({
    imports: [ArTeMiSSharedModule, ArTeMiSAdminModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [
        ExerciseResultComponent,
        ExerciseResultDetailComponent,
        ExerciseResultUpdateComponent,
        ExerciseResultDeleteDialogComponent,
        ExerciseResultDeletePopupComponent
    ],
    entryComponents: [
        ExerciseResultComponent,
        ExerciseResultUpdateComponent,
        ExerciseResultDeleteDialogComponent,
        ExerciseResultDeletePopupComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTeMiSExerciseResultModule {}
