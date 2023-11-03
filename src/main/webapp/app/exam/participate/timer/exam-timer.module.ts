import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    declarations: [ExamTimerComponent],
    imports: [CommonModule, ArtemisSharedCommonModule],
    exports: [ExamTimerComponent],
})
export class ArtemisExamTimerModule {}
