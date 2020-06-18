import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { examManagementState } from 'app/exam/manage/exam-management.route';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/exam-detail.component';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import { ExerciseGroupUpdateComponent } from 'app/exam/manage/exercise-groups/exercise-group-update.component';
import { ExerciseGroupDetailComponent } from 'app/exam/manage/exercise-groups/exercise-group-detail.component';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail.component';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { MomentModule } from 'ngx-moment';
import { DurationPipe } from 'app/shared/pipes/artemis-duration.pipe';
import { StudentsExamImportDialogComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-dialog.component';
import { StudentsExamImportButtonComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-button.component';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    // TODO: For better modularization we could define an exercise module with the corresponding exam routes
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisTextExerciseModule,
        ArtemisSharedModule,
        FormDateTimePickerModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownEditorModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisTextExerciseModule,
        ArtemisFileUploadExerciseManagementModule,
        MomentModule,
    ],
    declarations: [
        ExamManagementComponent,
        ExamUpdateComponent,
        ExamDetailComponent,
        ExerciseGroupsComponent,
        ExerciseGroupUpdateComponent,
        ExerciseGroupDetailComponent,
        ExamStudentsComponent,
        StudentExamsComponent,
        StudentsExamImportDialogComponent,
        StudentsExamImportButtonComponent,
        StudentExamDetailComponent,
        DurationPipe,
    ],
})
export class ArtemisExamManagementModule {}
