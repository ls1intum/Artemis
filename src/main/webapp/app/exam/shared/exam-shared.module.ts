import { NgModule } from '@angular/core';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time/student-exam-working-time.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { TestexamWorkingTimeComponent } from 'app/exam/shared/testExam-workingTime/testexam-working-time.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-update/working-time-control.component';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedCommonModule, ArtemisMarkdownModule],
    declarations: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent, WorkingTimeControlComponent, ExamLiveEventComponent],
    exports: [StudentExamWorkingTimeComponent, TestexamWorkingTimeComponent, WorkingTimeControlComponent, ExamLiveEventComponent],
})
export class ArtemisExamSharedModule {}
