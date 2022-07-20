import { NgModule } from '@angular/core';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { TestexamWorkingTimeComponent } from 'app/exam/shared/testExam-workingTime/testexam-working-time.component';

@NgModule({
    imports: [ArtemisSharedCommonModule],
    declarations: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent],
    exports: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent],
})
export class ArtemisExamSharedModule {}
