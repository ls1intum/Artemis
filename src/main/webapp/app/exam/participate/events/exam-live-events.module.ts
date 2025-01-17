import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { ExamLiveEventsOverlayComponent } from 'app/exam/participate/events/exam-live-events-overlay.component';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';

@NgModule({
    imports: [CommonModule, ArtemisSharedCommonModule, ArtemisExamSharedModule, ExamLiveEventsButtonComponent, ExamLiveEventsOverlayComponent],
    exports: [ExamLiveEventsButtonComponent, ExamLiveEventsOverlayComponent],
})
export class ArtemisExamLiveEventsModule {}
