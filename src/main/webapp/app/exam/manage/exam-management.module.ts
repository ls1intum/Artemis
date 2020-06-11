import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamManagementComponent } from 'app/exam/manage/exam-management.component';
import { ExamCoverComponent } from 'app/exam/participate/exam-cover/exam-cover.component';
import { examManagementState } from 'app/exam/manage/exam-management.route';

const ENTITY_STATES = [...examManagementState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExamCoverComponent],
})
export class ArtemisExamManagementModule {}
