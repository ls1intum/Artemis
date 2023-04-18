import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { assessmentDashboardRoute } from './assessment-dashboard.route';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisExerciseAssessmentDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';
import { AssessmentDashboardInformationComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { PieChartModule } from '@swimlane/ngx-charts';
import { ExamAssessmentButtonsComponent } from './exam-assessment-buttons/exam-assessment-buttons.component';

const ENTITY_STATES = [...assessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSidePanelModule,
        ArtemisResultModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTutorLeaderboardModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisExerciseAssessmentDashboardModule,
        ArtemisSharedComponentModule,
        PieChartModule,
    ],
    declarations: [AssessmentDashboardComponent, AssessmentDashboardInformationComponent, ExamAssessmentButtonsComponent],
})
export class ArtemisAssessmentDashboardModule {}
