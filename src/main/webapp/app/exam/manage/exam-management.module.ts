import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArtemisExamScoresModule } from 'app/exam/exam-scores/exam-scores.module';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { examManagementState } from 'app/exam/manage/exam-management.route';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-participation-summary.module';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisExamParticipantScoresModule } from 'app/exam/manage/exam-participant-scores/exam-participant-scores.module';
import { ProgrammingExerciseGroupCellComponent } from './exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { FileUploadExerciseGroupCellComponent } from './exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from './exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from './exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { BarChartModule } from '@swimlane/ngx-charts';
import { UserImportModule } from 'app/shared/import/user-import.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ExamStatusComponent } from 'app/exam/manage/exam-status.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    // TODO: For better modularization we could define an exercise module with the corresponding exam routes
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTextExerciseModule,
        ArtemisExamScoresModule,
        ArtemisSharedModule,
        FormDateTimePickerModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownEditorModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisTextExerciseModule,
        ArtemisFileUploadExerciseManagementModule,
        ArtemisProgrammingExerciseManagementModule,
        ArtemisQuizManagementModule,
        ArtemisParticipationSummaryModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisMarkdownModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisExamParticipantScoresModule,
        ArtemisTextSubmissionAssessmentModule,
        ExampleSubmissionsModule,
        UserImportModule,
        ArtemisExamSharedModule,
        ArtemisExamModePickerModule,
        BarChartModule,
    ],
    declarations: [
        ExamManagementComponent,
        ExamUpdateComponent,
        ExamDetailComponent,
        ExerciseGroupsComponent,
        ExerciseGroupUpdateComponent,
        ExamExerciseRowButtonsComponent,
        ExamStudentsComponent,
        StudentExamStatusComponent,
        StudentExamsComponent,
        TestRunManagementComponent,
        CreateTestRunModalComponent,
        StudentExamDetailComponent,
        DurationPipe,
        StudentExamSummaryComponent,
        ExamChecklistComponent,
        ExamChecklistExerciseGroupTableComponent,
        ExamChecklistCheckComponent,
        ExamStatusComponent,
        ProgrammingExerciseGroupCellComponent,
        FileUploadExerciseGroupCellComponent,
        ModelingExerciseGroupCellComponent,
        QuizExerciseGroupCellComponent,
        StudentExamDetailTableRowComponent,
    ],
})
export class ArtemisExamManagementModule {}
