import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { tutorCourseDashboardRoute } from './tutor-course-dashboard.route';
import { TutorCourseDashboardComponent } from './tutor-course-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { SortByModule } from 'app/shared/pipes/sort-by.module';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';

const ENTITY_STATES = [...tutorCourseDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArtemisTutorLeaderboardModule,
        ArtemisTutorParticipationGraphModule,
    ],
    declarations: [TutorCourseDashboardComponent],
})
export class ArtemisTutorCourseDashboardModule {}
