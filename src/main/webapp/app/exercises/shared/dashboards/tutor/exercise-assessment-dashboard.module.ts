import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { PieChartModule } from '@swimlane/ngx-charts';

import { ExerciseAssessmentDashboardComponent } from './exercise-assessment-dashboard.component';
import { exerciseAssessmentDashboardRoute } from './exercise-assessment-dashboard.route';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { LanguageTableCellComponent } from 'app/exercises/shared/dashboards/tutor/language-table-cell/language-table-cell.component';
import { SecondCorrectionEnableButtonComponent } from 'app/exercises/shared/dashboards/tutor/second-correction-button/second-correction-enable-button.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { OrionExerciseAssessmentDashboardComponent } from 'app/orion/assessment/orion-exercise-assessment-dashboard.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTutorLeaderboardModule } from 'app/shared/dashboards/tutor-leaderboard/tutor-leaderboard.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisInfoPanelModule } from 'app/shared/info-panel/info-panel.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const ENTITY_STATES = [...exerciseAssessmentDashboardRoute];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisResultModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisModelingEditorModule,
        AssessmentInstructionsModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSidePanelModule,
        ArtemisInfoPanelModule,
        ArtemisTutorLeaderboardModule,
        ArtemisSharedComponentModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisAssessmentSharedModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisMarkdownModule,
        OrionModule,
        SubmissionResultStatusModule,
        PieChartModule,
    ],
    declarations: [ExerciseAssessmentDashboardComponent, OrionExerciseAssessmentDashboardComponent, SecondCorrectionEnableButtonComponent, LanguageTableCellComponent],
    providers: [],
    exports: [SecondCorrectionEnableButtonComponent, LanguageTableCellComponent],
})
export class ArtemisExerciseAssessmentDashboardModule {}
