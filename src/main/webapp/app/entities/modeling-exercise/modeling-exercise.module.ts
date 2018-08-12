import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from 'ng-pick-datetime';

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
import { CourseModelingExerciseService } from '../course/course.service';
import { SortByModule, DatePipeModule } from '../../components/pipes';

const ENTITY_STATES = [
    ...modelingExerciseRoute,
    ...modelingExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        DatePipeModule,
        OwlDateTimeModule,
        OwlNativeDateTimeModule
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
        CourseModelingExerciseService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSModelingExerciseModule {}
