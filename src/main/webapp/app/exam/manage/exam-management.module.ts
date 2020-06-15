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
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule, FormDateTimePickerModule, ArtemisSharedComponentModule, ArtemisMarkdownEditorModule],
    declarations: [
        ExamManagementComponent,
        ExamUpdateComponent,
        ExamDetailComponent,
        ExerciseGroupsComponent,
        ExerciseGroupUpdateComponent,
        ExerciseGroupDetailComponent,
        ExamStudentsComponent,
        StudentExamsComponent,
        StudentExamDetailComponent,
    ],
})
export class ArtemisExamManagementModule {}
