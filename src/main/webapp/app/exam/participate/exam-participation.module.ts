import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationCoverComponent } from './exam-cover/exam-participation-cover.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingParticipationModule } from 'app/exercises/programming/participate/programming-participation.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-result-summary.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisExamNavigationBarModule } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.module';
import { ArtemisExamTimerModule } from 'app/exam/participate/timer/exam-timer.module';
import { ArtemisExamSubmissionComponentsModule } from 'app/exam/participate/exercises/exam-submission-components.module';
import { ExamExerciseUpdateHighlighterModule } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ArtemisExamLiveEventsModule } from 'app/exam/participate/events/exam-live-events.module';
import { ExamStartInformationComponent } from 'app/exam/participate/exam-start-information/exam-start-information.component';
import { ArtemisSidebarModule } from 'app/shared/sidebar/sidebar.module';
import { ExamNavigationSidebarComponent } from 'app/exam/participate/exam-navigation-sidebar/exam-navigation-sidebar.component';
const ENTITY_STATES = [...examParticipationState];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedCommonModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSharedModule,
        ArtemisModelingEditorModule,
        ArtemisQuizQuestionTypesModule,
        ArtemisFullscreenModule,
        ArtemisSharedComponentModule,
        ArtemisProgrammingParticipationModule,
        ArtemisCodeEditorModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisExerciseButtonsModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisParticipationSummaryModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
        ArtemisExamNavigationBarModule,
        ArtemisExamTimerModule,
        ArtemisExamSubmissionComponentsModule,
        ExamExerciseUpdateHighlighterModule,
        ArtemisExamSharedModule,
        ArtemisExamLiveEventsModule,
        ExamStartInformationComponent,
        ArtemisSidebarModule,
        ExamNavigationSidebarComponent,
    ],
    declarations: [ExamParticipationComponent, ExamParticipationCoverComponent, ExamExerciseOverviewPageComponent],
})
export class ArtemisExamParticipationModule {}
