import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ExamResultSummaryComponent } from 'app/exam/participate/summary/exam-result-summary.component';
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
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ExamGeneralInformationComponent } from 'app/exam/participate/general-information/exam-general-information.component';
import { ExamResultOverviewComponent } from 'app/exam/participate/summary/result-overview/exam-result-overview.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { TestRunRibbonComponent } from 'app/exam/manage/test-runs/test-run-ribbon.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ExampleSolutionComponent } from 'app/exercises/shared/example-solution/example-solution.component';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { GradingKeyOverviewModule } from 'app/grading-system/grading-key-overview/grading-key-overview.module';
import { ExamResultSummaryExerciseCardHeaderComponent } from 'app/exam/participate/summary/exercises/header/exam-result-summary-exercise-card-header.component';
import { ArtemisModelingParticipationModule } from 'app/exercises/modeling/participate/modeling-participation.module';
import { ArtemisTextParticipationModule } from 'app/exercises/text/participate/text-participation.module';
import { ArtemisFileUploadParticipationModule } from 'app/exercises/file-upload/participate/file-upload-participation.module';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { CollapsibleCardComponent } from 'app/exam/participate/summary/collapsible-card.component';
import { NoDataComponent } from 'app/shared/no-data-component';

@NgModule({
    imports: [
        RouterModule,
        ArtemisSharedCommonModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisQuizQuestionTypesModule,
        ArtemisModelingEditorModule,
        ArtemisFullscreenModule,
        ArtemisResultModule,
        ArtemisComplaintsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisProgrammingExerciseManagementModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
        ArtemisExamSharedModule,
        ArtemisSharedComponentModule,
        GradingKeyOverviewModule,
        ArtemisModelingParticipationModule,
        ArtemisTextParticipationModule,
        ArtemisFileUploadParticipationModule,
        ArtemisFeedbackModule,
        NoDataComponent,
    ],
    declarations: [
        ExamResultSummaryComponent,
        ProgrammingExamSummaryComponent,
        ModelingExamSummaryComponent,
        FileUploadExamSummaryComponent,
        TextExamSummaryComponent,
        QuizExamSummaryComponent,
        ExamGeneralInformationComponent,
        ExamResultOverviewComponent,
        ExamResultSummaryExerciseCardHeaderComponent,
        TestRunRibbonComponent,
        ExampleSolutionComponent,
        CollapsibleCardComponent,
    ],
    exports: [ExamResultSummaryComponent, ExamGeneralInformationComponent, TestRunRibbonComponent],
})
export class ArtemisParticipationSummaryModule {}
