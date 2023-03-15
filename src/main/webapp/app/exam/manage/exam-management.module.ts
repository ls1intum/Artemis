import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BarChartModule } from '@swimlane/ngx-charts';

import { FileUploadExerciseGroupCellComponent } from './exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from './exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { ProgrammingExerciseGroupCellComponent } from './exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from './exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ArtemisExamScoresModule } from 'app/exam/exam-scores/exam-scores.module';
import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { examManagementState } from 'app/exam/manage/exam-management.route';
import { ExamStatusComponent } from 'app/exam/manage/exam-status.component';
import { ExamChecklistCheckComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-check/exam-checklist-check.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentsUploadImagesModule } from 'app/exam/manage/students/upload-images/students-upload-images.module';
import { ExamStudentsAttendanceCheckComponent } from 'app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-participation-summary.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { BonusComponent } from 'app/grading-system/bonus/bonus.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { UserImportModule } from 'app/shared/import/user-import.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
        ArtemisTextSubmissionAssessmentModule,
        ExampleSubmissionsModule,
        UserImportModule,
        ArtemisExamSharedModule,
        ArtemisExamModePickerModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        BarChartModule,
        FeatureToggleModule,
        ArtemisModePickerModule,
        StudentsUploadImagesModule,
    ],
    declarations: [
        ExamManagementComponent,
        ExamUpdateComponent,
        ExamDetailComponent,
        ExerciseGroupsComponent,
        ExerciseGroupUpdateComponent,
        ExamExerciseRowButtonsComponent,
        ExamStudentsComponent,
        ExamStudentsAttendanceCheckComponent,
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
        ExamImportComponent,
        ExamExerciseImportComponent,
        BonusComponent,
    ],
})
export class ArtemisExamManagementModule {}
