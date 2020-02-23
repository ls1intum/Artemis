import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ChartsModule } from 'ng2-charts';
import { instructorCourseDashboardRoute } from './instructor-course-dashboard.route';
import { InstructorCourseDashboardComponent } from './instructor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisTutorLeaderboardModule } from 'app/course/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = instructorCourseDashboardRoute;

@NgModule({
    imports: [ArtemisSharedModule, SortByModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ChartsModule, ArtemisTutorLeaderboardModule],
    declarations: [InstructorCourseDashboardComponent],
    exports: [],
    entryComponents: [],
    providers: [],
})
export class ArtemisInstructorCourseStatsDashboardModule {}
