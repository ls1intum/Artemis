import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { TutorParticipationGraphComponent } from 'app/course/dashboards/tutor-course-dashboard/tutor-participation-graph/tutor-participation-graph.component';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisTutorLeaderboardModule } from 'app/course/dashboards/instructor-course-dashboard/tutor-leaderboard/tutor-leaderboard.module';
import { ProgressBarComponent } from 'app/course/dashboards/tutor-course-dashboard/tutor-participation-graph/progress-bar/progress-bar.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisTutorLeaderboardModule],
    declarations: [TutorCourseDashboardComponent, TutorParticipationGraphComponent, ProgressBarComponent],
    exports: [TutorParticipationGraphComponent, ProgressBarComponent],
    providers: [],
})
export class ArtemisTutorCourseDashboardModule {}
