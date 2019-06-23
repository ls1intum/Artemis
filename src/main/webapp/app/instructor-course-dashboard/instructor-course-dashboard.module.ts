import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { ChartsModule } from 'ng2-charts';

import { ArTEMiSSharedModule } from '../shared';
import { instructorCourseDashboardRoute } from './instructor-course-dashboard.route';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { SortByModule } from 'app/components/pipes';
import { ArTEMiSTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';

const ENTITY_STATES = instructorCourseDashboardRoute;

@NgModule({
    imports: [BrowserModule, ArTEMiSSharedModule, SortByModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ChartsModule, ArTEMiSTutorLeaderboardModule],
    declarations: [InstructorCourseDashboardComponent],
    exports: [],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArTEMiSInstructorCourseStatsDashboardModule {}
