import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    ExerciseComponent,
    ExerciseLtiConfigurationDialogComponent, ExerciseLtiConfigurationPopupComponent, ExerciseLtiConfigurationService,
    exercisePopupRoute,
    ExercisePopupService, ExerciseResetDialogComponent, ExerciseResetPopupComponent,
    exerciseRoute,
    ExerciseService
} from './';

const ENTITY_STATES = [
    ...exerciseRoute,
    ...exercisePopupRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        ExerciseComponent,
        ExerciseLtiConfigurationDialogComponent,
        ExerciseLtiConfigurationPopupComponent,
        ExerciseResetDialogComponent,
        ExerciseResetPopupComponent
    ],
    entryComponents: [
        ExerciseComponent,
        ExerciseLtiConfigurationDialogComponent,
        ExerciseLtiConfigurationPopupComponent,
        ExerciseResetDialogComponent,
        ExerciseResetPopupComponent
    ],
    providers: [
        ExercisePopupService,
        ExerciseService,
        ExerciseLtiConfigurationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSExerciseModule {}
