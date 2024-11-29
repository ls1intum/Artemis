import { Component, EventEmitter, Output, input } from '@angular/core';
import { faCheck, faEye, faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import {
    ExamAttendanceCheckEvent,
    ExamLiveEvent,
    ExamLiveEventType,
    ExamWideAnnouncementEvent,
    ProblemStatementUpdateEvent,
    WorkingTimeUpdateEvent,
} from 'app/exam/participate/exam-participation-live-events.service';
import { NgClass } from '@angular/common';
import { TranslateDirective } from '../../../shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { WorkingTimeChangeComponent } from '../working-time-change/working-time-change.component';
import { ArtemisSharedCommonModule } from '../../../shared/shared-common.module';
import { ArtemisMarkdownModule } from '../../../shared/markdown.module';

@Component({
    selector: 'jhi-exam-live-event',
    templateUrl: './exam-live-event.component.html',
    styleUrls: ['./exam-live-event.component.scss'],
    standalone: true,
    imports: [NgClass, TranslateDirective, FaIconComponent, WorkingTimeChangeComponent, ArtemisSharedCommonModule, ArtemisMarkdownModule],
})
export class ExamLiveEventComponent {
    event = input.required<ExamLiveEvent>();

    showAcknowledge = input<boolean>(false);

    @Output()
    onAcknowledge = new EventEmitter<ExamLiveEvent>();

    @Output()
    onNavigate = new EventEmitter<ExamLiveEvent>();

    protected readonly ExamLiveEventType = ExamLiveEventType;

    // Icons
    faCheck = faCheck;
    faPaperPlane = faPaperPlane;
    faEye = faEye;

    get examWideAnnouncementEvent(): ExamWideAnnouncementEvent {
        return this.event() as ExamWideAnnouncementEvent;
    }

    get examAttendanceCheckEvent(): ExamAttendanceCheckEvent {
        return this.event() as ExamAttendanceCheckEvent;
    }

    get workingTimeUpdateEvent(): WorkingTimeUpdateEvent {
        return this.event() as WorkingTimeUpdateEvent;
    }

    get problemStatementUpdateEvent(): ProblemStatementUpdateEvent {
        return this.event() as ProblemStatementUpdateEvent;
    }

    acknowledgeEvent() {
        this.onAcknowledge.emit(this.event());
    }

    navigateToExercise() {
        this.onNavigate.emit(this.event());
    }
}
