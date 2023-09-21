import { NgModule } from '@angular/core';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time/student-exam-working-time.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { TestexamWorkingTimeComponent } from 'app/exam/shared/testExam-workingTime/testexam-working-time.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-update/working-time-control.component';

@NgModule({
    imports: [ArtemisSharedCommonModule],
    declarations: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent, WorkingTimeControlComponent],
    exports: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent, WorkingTimeControlComponent],
})
export class ArtemisExamSharedModule {}
