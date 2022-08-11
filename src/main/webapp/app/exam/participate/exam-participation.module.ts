import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { ExamParticipationCoverComponent } from './exam-cover/exam-participation-cover.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ExamNavigationBarComponent } from './exam-navigation-bar/exam-navigation-bar.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { ArtemisProgrammingParticipationModule } from 'app/exercises/programming/participate/programming-participation.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-participation-summary.module';
import { ExamTimerComponent } from './timer/exam-timer.component';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

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
