import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { assessmentDashboardRoute } from './assessment-dashboard.route';
import { AssessmentDashboardComponent } from './assessment-dashboard.component';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisExerciseAssessmentDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';
import { AssessmentDashboardInformationComponent } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard-information.component';

import { PieChartModule } from '@swimlane/ngx-charts';
import { ExamAssessmentButtonsComponent } from './exam-assessment-buttons/exam-assessment-buttons.component';

const ENTITY_STATES = [...assessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisResultModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTutorLeaderboardModule,
        ArtemisExerciseAssessmentDashboardModule,

        PieChartModule,
        AssessmentDashboardComponent,
        AssessmentDashboardInformationComponent,
        ExamAssessmentButtonsComponent,
    ],
})
export class ArtemisAssessmentDashboardModule {}
