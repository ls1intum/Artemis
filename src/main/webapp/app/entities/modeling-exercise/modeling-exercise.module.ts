import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../../shared';
import {
    ModelingExerciseComponent,
    ModelingExerciseDeleteDialogComponent,
    ModelingExerciseDeletePopupComponent,
    ModelingExerciseDetailComponent,
    ModelingExerciseDialogComponent,
    ModelingExercisePopupComponent,
    modelingExercisePopupRoute,
    ModelingExercisePopupService,
    modelingExerciseRoute,
    ModelingExerciseService
} from './';
import { SortByModule } from '../../components/pipes';
import { FormDateTimePickerModule } from '../../shared/dateTimePicker/date-time-picker.module';

const ENTITY_STATES = [
    ...modelingExerciseRoute,
    ...modelingExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        FormDateTimePickerModule,
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
export class ArTEMiSModelingExerciseModule {}
