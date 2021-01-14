import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { exerciseAssessmentDashboardRoute } from './exercise-assessment-dashboard.route';
import { ExerciseAssessmentDashboardComponent } from './exercise-assessment-dashboard.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisAssessmentDashboardModule } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';

const ENTITY_STATES = [...exerciseAssessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
        ClipboardModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisAssessmentDashboardModule,
        ArtemisModelingEditorModule,
        AssessmentInstructionsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSidePanelModule,
        ArtemisTutorLeaderboardModule,
        ArtemisSharedComponentModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisAssessmentSharedModule,
        ArtemisTutorParticipationGraphModule,
    ],
    declarations: [ExerciseAssessmentDashboardComponent],
    providers: [],
})
export class ArtemisExerciseAssessmentDashboardModule {}
