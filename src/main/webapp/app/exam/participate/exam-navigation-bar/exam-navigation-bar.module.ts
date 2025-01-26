import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisExamTimerModule } from 'app/exam/participate/timer/exam-timer.module';
import { ArtemisExamLiveEventsModule } from 'app/exam/participate/events/exam-live-events.module';

@NgModule({
    imports: [CommonModule, ArtemisSharedCommonModule, ArtemisExamTimerModule, ArtemisExamLiveEventsModule, ExamNavigationBarComponent],
    exports: [ExamNavigationBarComponent],
})
export class ArtemisExamNavigationBarModule {}
