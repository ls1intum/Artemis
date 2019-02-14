import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'angular2-moment';

import { ArTEMiSSharedModule } from '../shared';
import { ExerciseTypePipe } from '../entities/exercise/';

import {
    CourseExerciseRowComponent,
    CourseExercisesComponent,
    CourseOverviewComponent,
    CourseGradeBookComponent,
    CourseStatisticsComponent,
    OVERVIEW_ROUTES,
    OverviewComponent,
    OverviewCourseCardComponent,
    ExerciseActionButtonComponent,
    CourseExerciseDetailsComponent,
    ExerciseDetailsStudentActionsComponent
} from './';
import { ArTEMiSResultModule } from 'app/entities/result';

const ENTITY_STATES = [
    ...OVERVIEW_ROUTES
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArTEMiSResultModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        OverviewComponent,
        CourseOverviewComponent,
        OverviewCourseCardComponent,
        CourseStatisticsComponent,
        CourseExerciseRowComponent,
        CourseExercisesComponent,
        CourseExerciseDetailsComponent,
        ExerciseActionButtonComponent,
        CourseGradeBookComponent,
        ExerciseDetailsStudentActionsComponent,
        ExerciseTypePipe
    ],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSOverviewModule {
}
