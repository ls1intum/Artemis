import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamParticipationCoverComponent } from './exam-cover/exam-participation-cover.component';
import { ExamNavigationBarComponent } from './exam-navigation-bar/exam-navigation-bar.component';
import { ExamTimerComponent } from './timer/exam-timer.component';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-participation-summary.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisProgrammingParticipationModule } from 'app/exercises/programming/participate/programming-participation.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
        ArtemisCoursesModule,
        ArtemisExerciseButtonsModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisParticipationSummaryModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
    ],
    declarations: [
        ExamParticipationComponent,
        ExamParticipationCoverComponent,
        QuizExamSubmissionComponent,
        ProgrammingExamSubmissionComponent,
        TextExamSubmissionComponent,
        ModelingExamSubmissionComponent,
        FileUploadExamSubmissionComponent,
        ExamNavigationBarComponent,
        ExamTimerComponent,
        ExamExerciseOverviewPageComponent,
        ExamExerciseUpdateHighlighterComponent,
    ],
})
export class ArtemisExamParticipationModule {}
