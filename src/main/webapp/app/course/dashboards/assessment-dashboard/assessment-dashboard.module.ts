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
import { AssessmentDashboardInformationComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const ENTITY_STATES = [...assessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSidePanelModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTutorLeaderboardModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisExerciseAssessmentDashboardModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [AssessmentDashboardComponent, AssessmentDashboardInformationComponent],
})
export class ArtemisAssessmentDashboardModule {}
