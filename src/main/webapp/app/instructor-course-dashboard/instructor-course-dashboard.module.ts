import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { ChartsModule } from 'ng2-charts';

import { ArTEMiSSharedModule } from '../shared';
import { instructorCourseDashboardRoute } from './instructor-course-dashboard.route';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { MomentModule } from 'angular2-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorLeaderboardComponent } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.component';

const ENTITY_STATES = instructorCourseDashboardRoute;

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ChartsModule],
    declarations: [InstructorCourseDashboardComponent, TutorLeaderboardComponent],
    exports: [TutorLeaderboardComponent],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSInstructorCourseStatsDashboardModule {}
