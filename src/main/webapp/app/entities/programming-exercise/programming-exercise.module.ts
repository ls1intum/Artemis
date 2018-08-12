import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from 'ng-pick-datetime';
import { BrowserModule } from '@angular/platform-browser';

import { ArTEMiSSharedModule } from '../../shared';
import {
    ProgrammingExerciseComponent,
    ProgrammingExerciseDeleteDialogComponent,
    ProgrammingExerciseDeletePopupComponent,
    ProgrammingExerciseDetailComponent,
    ProgrammingExerciseDialogComponent,
    ProgrammingExercisePopupComponent,
    programmingExercisePopupRoute,
    ProgrammingExercisePopupService,
    programmingExerciseRoute,
    ProgrammingExerciseService,
} from './';
import { CourseProgrammingExerciseService } from '../course/course.service';
import { SortByModule, DatePipeModule } from '../../components/pipes';

const ENTITY_STATES = [
    ...programmingExerciseRoute,
    ...programmingExercisePopupRoute,
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        OwlDateTimeModule,
        OwlNativeDateTimeModule,
        DatePipeModule
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
        CourseProgrammingExerciseService,
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSProgrammingExerciseModule {}
