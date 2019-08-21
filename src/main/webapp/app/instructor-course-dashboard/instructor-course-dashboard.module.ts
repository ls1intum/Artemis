import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ChartsModule } from 'ng2-charts';

import { ArtemisSharedModule } from '../shared';
import { instructorCourseDashboardRoute } from './instructor-course-dashboard.route';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { SortByModule } from 'app/components/pipes';
import { ArtemisTutorLeaderboardModule } from 'app/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';

const ENTITY_STATES = instructorCourseDashboardRoute;

@NgModule({
    imports: [ArtemisSharedModule, SortByModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ChartsModule, ArtemisTutorLeaderboardModule],
    declarations: [InstructorCourseDashboardComponent],
    exports: [],
    entryComponents: [],
    providers: [],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
})
export class ArtemisInstructorCourseStatsDashboardModule {}
