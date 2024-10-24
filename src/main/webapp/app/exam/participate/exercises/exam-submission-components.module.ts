import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExamExerciseUpdateHighlighterModule } from 'app/exam/participate/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisProgrammingSubmissionPolicyStatusModule } from 'app/exercises/programming/participate/programming-submission-policy-status.module';
import { ExerciseSaveButtonComponent } from './exercise-save-button/exercise-save-button.component';

@NgModule({
    declarations: [
        FileUploadExamSubmissionComponent,
        QuizExamSubmissionComponent,
        ProgrammingExamSubmissionComponent,
        TextExamSubmissionComponent,
        ModelingExamSubmissionComponent,
    ],
    imports: [
        CommonModule,
        ArtemisSharedModule,
        ArtemisMarkdownModule,
        ArtemisSharedComponentModule,
        ArtemisQuizQuestionTypesModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisExerciseButtonsModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisCodeEditorModule,
        ArtemisFullscreenModule,
        ArtemisModelingEditorModule,
        ArtemisProgrammingSubmissionPolicyStatusModule,
        ExamExerciseUpdateHighlighterModule,
        ExerciseSaveButtonComponent,
    ],
    exports: [FileUploadExamSubmissionComponent, QuizExamSubmissionComponent, ProgrammingExamSubmissionComponent, TextExamSubmissionComponent, ModelingExamSubmissionComponent],
})
export class ArtemisExamSubmissionComponentsModule {}
