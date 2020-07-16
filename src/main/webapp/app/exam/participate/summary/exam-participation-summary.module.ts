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
    ],
    declarations: [
        ExamParticipationSummaryComponent,
        ProgrammingExamSummaryComponent,
        ModelingExamSummaryComponent,
        FileUploadExamSummaryComponent,
        TextExamSummaryComponent,
        QuizExamSummaryComponent,
    ],
    exports: [ExamParticipationSummaryComponent],
})
export class ArtemisParticipationSummaryModule {}
