import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { instructorExerciseDashboardRoute } from './instructor-exercise-dashboard.route';
import { InstructorExerciseDashboardComponent } from './instructor-exercise-dashboard.component';
import { MomentModule } from 'angular2-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ChartsModule } from 'ng2-charts';
import { ArTEMiSInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard';

const ENTITY_STATES = instructorExerciseDashboardRoute;

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ChartsModule, ArTEMiSInstructorCourseStatsDashboardModule],
    declarations: [InstructorExerciseDashboardComponent],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSInstructorExerciseStatsDashboardModule {}
