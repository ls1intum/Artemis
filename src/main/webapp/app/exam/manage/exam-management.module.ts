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
import { StudentsUploadImagesModule } from 'app/exam/manage/students/upload-images/students-upload-images.module';
import { ExamStudentsAttendanceCheckComponent } from 'app/exam/manage/students/verify-attendance-check/exam-students-attendance-check.component';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status/student-exam-status.component';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { ArtemisParticipationSummaryModule } from 'app/exam/participate/summary/exam-result-summary.module';
import { ExamExerciseRowButtonsComponent } from 'app/exercises/shared/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { TestRunManagementComponent } from 'app/exam/manage/test-runs/test-run-management.component';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExamChecklistComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.component';
import { ExamChecklistExerciseGroupTableComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist-exercisegroup-table/exam-checklist-exercisegroup-table.component';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ProgrammingExerciseGroupCellComponent } from './exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { FileUploadExerciseGroupCellComponent } from './exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from './exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from './exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ArtemisTextSubmissionAssessmentModule } from 'app/exercises/text/assess/text-submission-assessment.module';
import { StudentExamDetailTableRowComponent } from 'app/exam/manage/student-exams/student-exam-detail-table-row/student-exam-detail-table-row.component';
import { ExampleSubmissionsModule } from 'app/exercises/shared/example-submission/example-submissions.module';
import { BarChartModule } from '@swimlane/ngx-charts';
import { UserImportModule } from 'app/shared/user-import/user-import.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ExamStatusComponent } from 'app/exam/manage/exam-status.component';
import { ArtemisExamModePickerModule } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.module';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { BonusComponent } from 'app/grading-system/bonus/bonus.component';
import { ArtemisModePickerModule } from 'app/exercises/shared/mode-picker/mode-picker.module';
import { StudentExamTimelineComponent } from './student-exams/student-exam-timeline/student-exam-timeline.component';
import { TitleChannelNameModule } from 'app/shared/form/title-channel-name/title-channel-name.module';
import { ExamEditWorkingTimeDialogComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time-dialog.component';
import { SuspiciousBehaviorComponent } from './suspicious-behavior/suspicious-behavior.component';
import { SuspiciousSessionsOverviewComponent } from './suspicious-behavior/suspicious-sessions-overview/suspicious-sessions-overview.component';
import { PlagiarismCasesOverviewComponent } from './suspicious-behavior/plagiarism-cases-overview/plagiarism-cases-overview.component';
import { SuspiciousSessionsComponent } from './suspicious-behavior/suspicious-sessions/suspicious-sessions.component';
import { ExamEditWorkingTimeComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time.component';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';

import { ArtemisExamNavigationBarModule } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.module';
import { ArtemisExamSubmissionComponentsModule } from 'app/exam/participate/exercises/exam-submission-components.module';
import { MatSliderModule } from '@angular/material/slider';
import { ProgrammingExerciseExamDiffComponent } from './student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { NoDataComponent } from 'app/shared/no-data-component';
import { GitDiffLineStatComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    // TODO: For better modularization we could define an exercise module with the corresponding exam routes
    providers: [ArtemisDurationFromSecondsPipe],
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
        TitleChannelNameModule,
        ArtemisExamNavigationBarModule,
        ArtemisExamSubmissionComponentsModule,
        MatSliderModule,
        GitDiffReportModule,
        ArtemisProgrammingExerciseModule,
        DetailModule,
        NoDataComponent,
        GitDiffLineStatComponent,
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
        ExamStatusComponent,
        ProgrammingExerciseGroupCellComponent,
        FileUploadExerciseGroupCellComponent,
        ModelingExerciseGroupCellComponent,
        QuizExerciseGroupCellComponent,
        StudentExamDetailTableRowComponent,
        ExamImportComponent,
        ExamExerciseImportComponent,
        BonusComponent,
        ExamEditWorkingTimeComponent,
        ExamEditWorkingTimeDialogComponent,
        ExamLiveAnnouncementCreateModalComponent,
        ExamLiveAnnouncementCreateButtonComponent,
        SuspiciousBehaviorComponent,
        SuspiciousSessionsOverviewComponent,
        PlagiarismCasesOverviewComponent,
        SuspiciousSessionsComponent,
        StudentExamTimelineComponent,
        ProgrammingExerciseExamDiffComponent,
    ],
})
export class ArtemisExamManagementModule {}
