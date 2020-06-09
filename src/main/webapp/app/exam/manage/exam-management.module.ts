import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { examManagementState } from 'app/exam/manage/exam-management.route';
import { ExamUpdateComponent } from 'app/exam/manage/exams/exam-update.component';
import { ExamDetailComponentComponent } from 'app/exam/manage/exams/exam-detail.component';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExamManagementComponent, ExamUpdateComponent, ExamDetailComponentComponent],
})
export class ArtemisExamManagementModule {}
