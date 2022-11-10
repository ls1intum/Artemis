import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { ProgrammingExamSummaryComponent } from 'app/exam/participate/summary/exercises/programming-exam-summary/programming-exam-summary.component';
import { ModelingExamSummaryComponent } from 'app/exam/participate/summary/exercises/modeling-exam-summary/modeling-exam-summary.component';
import { FileUploadExamSummaryComponent } from 'app/exam/participate/summary/exercises/file-upload-exam-summary/file-upload-exam-summary.component';
import { TextExamSummaryComponent } from 'app/exam/participate/summary/exercises/text-exam-summary/text-exam-summary.component';
import { QuizExamSummaryComponent } from 'app/exam/participate/summary/exercises/quiz-exam-summary/quiz-exam-summary.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ExamPointsSummaryComponent } from 'app/exam/participate/summary/points-summary/exam-points-summary.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ExampleSolutionComponent } from 'app/exercises/shared/example-solution/example-solution.component';

@NgModule({
    imports: [
        RouterModule,
        ArtemisSharedCommonModule,
        ArtemisSharedModule,
        ArtemisQuizQuestionTypesModule,
        ArtemisModelingEditorModule,
        ArtemisFullscreenModule,
        ArtemisResultModule,
        ArtemisCoursesModule,
        ArtemisComplaintsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
        ArtemisExamSharedModule,
    ],
    declarations: [
        ExamParticipationSummaryComponent,
        ProgrammingExamSummaryComponent,
        ModelingExamSummaryComponent,
        FileUploadExamSummaryComponent,
        TextExamSummaryComponent,
        QuizExamSummaryComponent,
        ExamInformationComponent,
        ExamPointsSummaryComponent,
        TestRunRibbonComponent,
        ExampleSolutionComponent,
    ],
    exports: [ExamParticipationSummaryComponent, ExamInformationComponent, TestRunRibbonComponent],
})
export class ArtemisParticipationSummaryModule {}
