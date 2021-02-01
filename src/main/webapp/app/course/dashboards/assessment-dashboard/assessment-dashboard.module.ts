import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { assessmentDashboardRoute } from './assessment-dashboard.route';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisExerciseAssessmentDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';

const ENTITY_STATES = [...assessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTutorLeaderboardModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisExerciseAssessmentDashboardModule,
    ],
    declarations: [AssessmentDashboardComponent],
})
export class ArtemisAssessmentDashboardModule {}
