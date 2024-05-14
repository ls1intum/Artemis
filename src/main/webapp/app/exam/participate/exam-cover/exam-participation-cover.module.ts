import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamParticipationCoverComponent } from './exam-participation-cover.component';
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
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-result-summary.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisExamNavigationBarModule } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.module';
import { ArtemisExamTimerModule } from 'app/exam/participate/timer/exam-timer.module';
import { ArtemisExamSubmissionComponentsModule } from 'app/exam/participate/exercises/exam-submission-components.module';
import { ExamExerciseUpdateHighlighterModule } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ArtemisExamLiveEventsModule } from 'app/exam/participate/events/exam-live-events.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { Routes } from '@angular/router';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

const routes: Routes = [
    {
        path: '',
        component: ExamParticipationCoverComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.pages.courseTutorialGroupDetail.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        RouterModule.forChild(routes),
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
        ArtemisCoursesModule,
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
    ],
    declarations: [ExamParticipationCoverComponent],
})
export class ArtemisExamParticipationCoverModule {}
