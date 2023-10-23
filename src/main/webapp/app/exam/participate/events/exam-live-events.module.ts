import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisExamTimerModule } from 'app/exam/participate/timer/exam-timer.module';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { ExamLiveEventsOverlayComponent } from 'app/exam/participate/events/exam-live-events-overlay.component';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';

@NgModule({
    declarations: [ExamLiveEventsButtonComponent, ExamLiveEventsOverlayComponent],
    imports: [CommonModule, ArtemisSharedCommonModule, ArtemisExamTimerModule, ArtemisExamSharedModule],
    exports: [ExamLiveEventsButtonComponent, ExamLiveEventsOverlayComponent],
})
export class ArtemisExamLiveEventsModule {}
